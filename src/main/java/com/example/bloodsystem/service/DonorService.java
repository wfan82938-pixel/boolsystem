package com.example.bloodsystem.service;

import com.example.bloodsystem.config.MatchConfig;
import com.example.bloodsystem.entity.Donor;
import com.example.bloodsystem.repository.DonorRepository;
import com.example.bloodsystem.util.HlaUtils;
import com.example.bloodsystem.util.HlaUtils.HlaInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DonorService {

    @Autowired private DonorRepository repository;
    @Autowired private ImportService importService;
    @Autowired private MatchConfig matchConfig;
    @PersistenceContext private EntityManager entityManager;

    // --- CRUD ---
    @Transactional
    public void deleteDonor(String id) { try { repository.deleteById(id); repository.flush(); } catch (Exception e) { throw new RuntimeException("删除失败"); } }
    @Transactional
    public void deleteAllDonors() { try { repository.deleteAllInBatch(); repository.flush(); } catch (Exception e) { throw new RuntimeException("清空失败"); } }
    public Page<Donor> getDonors(int page, int size, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("donorId").descending());
        if (keyword != null && !keyword.trim().isEmpty()) return repository.search(keyword.trim(), pageable);
        return repository.findAll(pageable);
    }
    public Donor getDonorById(String id) { return repository.findById(id).orElse(null); }
    @Transactional
    public void saveDonor(Donor donor) {
        if (donor.getDonorId() == null || donor.getDonorId().isEmpty()) donor.setDonorId(UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        HlaUtils.fillSplitFields(donor);
        repository.save(donor);
    }
    public ImportResult importFromText(String textData) { return importService.parseAndImportText(textData); }

    // --- 新版配型逻辑 ---

    @SuppressWarnings("unchecked")
    public List<MatchResult> matchDonors(String patientBloodType,
                                         Map<String, String> pGts,
                                         String antibodyText, // 新增：抗体文本
                                         boolean limitResult) { // 新增：是否限制返回数量

        // 1. 解析患者数据
        Set<String> selectedHpas = new HashSet<>();
        List<String> validHpas = matchConfig.getAllHpas();

        if (pGts != null) {
            pGts.forEach((k, v) -> {
                if (v != null && !v.isEmpty() && validHpas.contains(k)) selectedHpas.add(k);
            });
        }

        // 解析患者 HLA (仅需 Group)
        HlaInfo tA1 = HlaUtils.parseHla(pGts.get("HLA-A1"));
        HlaInfo tA2 = HlaUtils.parseHla(pGts.get("HLA-A2"));
        HlaInfo tB1 = HlaUtils.parseHla(pGts.get("HLA-B1"));
        HlaInfo tB2 = HlaUtils.parseHla(pGts.get("HLA-B2"));

        // 解析抗体列表 (得到被禁止的 Group ID 列表)
        List<Integer> bannedGroups = HlaUtils.parseAntibodies(antibodyText);

        // 2. 构建查询 (全库扫描，不再限制 SQL 数量，因为需要处理所有人的抗体排斥)
        StringBuilder sql = new StringBuilder("SELECT * FROM donors WHERE 1=1 ");
        Map<String, Object> params = new HashMap<>();

        if (patientBloodType != null && !patientBloodType.isEmpty()) {
            sql.append(" AND blood_type = :bloodType ");
            params.put("bloodType", patientBloodType);
        }

        // HPA 筛选逻辑保持：如果选择了 HPA 且非严格模式，这里不卡死，依靠后续打分。
        // 为了性能，如果全库非常大，这里应该加索引筛选。考虑到目前逻辑是全显示，暂不加 WHERE 限制 HLA。

        Query nativeQuery = entityManager.createNativeQuery(sql.toString(), Donor.class);
        params.forEach(nativeQuery::setParameter);

        List<Donor> candidates = nativeQuery.getResultList();
        List<MatchResult> results = new ArrayList<>();

        // 3. 遍历每一个供者进行打分
        for (Donor d : candidates) {
            MatchResult mr = calculateScore(d, pGts, selectedHpas,
                    tA1, tA2, tB1, tB2,
                    bannedGroups);
            if (mr != null) {
                results.add(mr);
            }
        }

        // 4. 排序：分数高的在前 (负分自然在最后)
        results.sort((r1, r2) -> Double.compare(r2.score, r1.score));

        // 5. 根据参数返回 全部 或 前50
        if (limitResult && results.size() > 50) {
            return results.subList(0, 50);
        }
        return results;
    }

    private MatchResult calculateScore(Donor d, Map<String, String> pGts, Set<String> selectedHpaLoci,
                                       HlaInfo tA1, HlaInfo tA2, HlaInfo tB1, HlaInfo tB2,
                                       List<Integer> bannedGroups) {

        MatchResult mr = new MatchResult(d);
        double totalScore = 0.0;

        // --- 1. 抗体检测 (Antibody Check) ---
        // 规则：无论用户输入什么 HLA，都要检查供者 4 个位点是否命中抗体
        // 命中一个扣 1000 分
        checkConflict(d.getHlaA1Group(), "HLA-A1", bannedGroups, mr);
        checkConflict(d.getHlaA2Group(), "HLA-A2", bannedGroups, mr);
        checkConflict(d.getHlaB1Group(), "HLA-B1", bannedGroups, mr);
        checkConflict(d.getHlaB2Group(), "HLA-B2", bannedGroups, mr);

        // --- 2. HLA 适配 (HLA Matching) ---
        // 规则：只检查第一个数字 (Group)。匹配 +100 分。
        // 计算匹配的条数：0-4 条 -> 对应等级 D, C, B, A

        int matchCount = 0;

        // A 位点匹配逻辑：
        // 患者 A1 能匹配 供者 A1 或 A2 (单向，找最大匹配数)
        // 这里的逻辑需要细化：如果患者有 A1, A2。供者有 A1, A2。
        // 这是一个最大二分匹配问题，但通常简化为：
        // 尝试 顺向 (P1-D1, P2-D2) 和 交叉 (P1-D2, P2-D1) 哪种匹配数多

        int matchesA = countBestMatches(tA1, tA2, d.getHlaA1Group(), d.getHlaA2Group(), mr, "HLA-A");
        int matchesB = countBestMatches(tB1, tB2, d.getHlaB1Group(), d.getHlaB2Group(), mr, "HLA-B");

        matchCount = matchesA + matchesB;
        totalScore += (matchCount * 100.0);

        // 设置等级
        if (matchCount == 4) mr.grade = "A";
        else if (matchCount == 3) mr.grade = "B";
        else if (matchCount == 1 || matchCount == 2) mr.grade = "C";
        else mr.grade = "D";

        // --- 3. HPA 适配 (HPA Matching) ---
        // 规则：适配 +5，兼容 +2，不适配 0
        // 同时计算 HPA 匹配率 (Rate) 用于进度条显示

        double hpaScore = 0.0;
        double currentHpaWeight = 0.0;
        double maxHpaWeight = 0.0; // 用于计算百分比

        for (String locus : matchConfig.getAllHpas()) {
            if (!selectedHpaLoci.contains(locus)) continue;

            String pVal = pGts.get(locus);
            String dVal = d.getGenotype(locus);

            maxHpaWeight += 5.0; // 假设满分是匹配

            if (dVal == null || dVal.isEmpty()) {
                mr.unknownLoci.add(locus);
            } else {
                int pts = matchConfig.getScore(pVal, dVal); // 2=匹配, 1=兼容, 0=不匹配
                if (pts == 2) {
                    mr.matchedLoci.add(locus);
                    hpaScore += 5.0;
                    currentHpaWeight += 5.0;
                } else if (pts == 1) {
                    mr.compatibleLoci.add(locus);
                    hpaScore += 2.0;
                    currentHpaWeight += 2.0;
                } else {
                    mr.mismatchedLoci.add(locus);
                    // 不扣分
                }
            }
        }

        totalScore += hpaScore;
        // 如果有抗体冲突扣分，总分会变成负数
        totalScore -= (mr.conflictCount * 1000.0);

        mr.score = totalScore;
        mr.rate = (maxHpaWeight > 0) ? (currentHpaWeight / maxHpaWeight) * 100.0 : 0;
        if (mr.rate > 100) mr.rate = 100;

        return mr;
    }

    private void checkConflict(Integer donorGroup, String label, List<Integer> bannedGroups, MatchResult mr) {
        if (donorGroup == null) return;
        if (bannedGroups.contains(donorGroup)) {
            mr.conflictCount++;
            mr.conflictReasons.add(label + " (Group " + donorGroup + ") 包含排斥抗原");
        }
    }

    /**
     * 计算某一位点 (A 或 B) 的最佳匹配数 (0, 1, 2)
     * 同时处理高亮标记
     */
    private int countBestMatches(HlaInfo p1, HlaInfo p2, Integer d1, Integer d2, MatchResult mr, String type) {
        // 如果患者没输该位点，直接返回0
        if (p1 == null && p2 == null) return 0;

        // 辅助函数：单次匹配 (只比对 Group)
        // 供者 d1, d2 可能为 null

        // 方案 1: 顺向 P1-D1, P2-D2
        boolean m1_1 = isMatch(p1, d1);
        boolean m1_2 = isMatch(p2, d2);
        int score1 = (m1_1 ? 1 : 0) + (m1_2 ? 1 : 0);

        // 方案 2: 交叉 P1-D2, P2-D1
        boolean m2_1 = isMatch(p1, d2);
        boolean m2_2 = isMatch(p2, d1);
        int score2 = (m2_1 ? 1 : 0) + (m2_2 ? 1 : 0);

        // 选最大的，并记录高亮
        if (score1 >= score2) {
            if (m1_1) mr.highlightedAlleles.add(type + "1"); // 供者第1条链高亮
            if (m1_2) mr.highlightedAlleles.add(type + "2"); // 供者第2条链高亮
            if (score1 > 0) mr.matchedLoci.add(type);
            return score1;
        } else {
            if (m2_2) mr.highlightedAlleles.add(type + "1"); // P2 配 D1 -> D1 高亮
            if (m2_1) mr.highlightedAlleles.add(type + "2"); // P1 配 D2 -> D2 高亮
            if (score2 > 0) mr.matchedLoci.add(type);
            return score2;
        }
    }

    private boolean isMatch(HlaInfo p, Integer dGroup) {
        if (p == null || dGroup == null) return false;
        return p.group == dGroup;
    }

    public static class MatchResult {
        public Donor donor;
        public double rate;
        public double score;
        public String grade = "D"; // A, B, C, D

        // 匹配详情
        public List<String> matchedLoci = new ArrayList<>();
        public List<String> compatibleLoci = new ArrayList<>();
        public List<String> mismatchedLoci = new ArrayList<>();
        public List<String> unknownLoci = new ArrayList<>();

        // 高亮 (HLA-A1, HLA-A2, HLA-B1, HLA-B2)
        public Set<String> highlightedAlleles = new HashSet<>();

        // 冲突详情
        public int conflictCount = 0;
        public List<String> conflictReasons = new ArrayList<>();

        public MatchResult(Donor d) {
            this.donor = d;
        }
    }
}