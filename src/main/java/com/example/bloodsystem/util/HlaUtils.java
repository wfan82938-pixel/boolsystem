package com.example.bloodsystem.util;

import com.example.bloodsystem.entity.Donor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HlaUtils {

    // 解析 HLA 格式: 允许 HLA-A*02:01, A*02, 02:01, 02 等格式
    // 捕获组1: Group (第一段数字)
    // 捕获组2: Specific (第二段数字，可选)
    private static final Pattern HLA_PATTERN = Pattern.compile(".*?(\\d+)(?:[: ]*(\\d+))?.*");

    public static class HlaInfo {
        public int group;
        public int specific; // -1 表示未指定

        public HlaInfo(int group, int specific) {
            this.group = group;
            this.specific = specific;
        }
    }

    public static HlaInfo parseHla(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        // 清理字符串：去除中文冒号、星号、无关字符
        String cleaned = raw.trim().replace("：", ":").replace("*", "");
        Matcher matcher = HLA_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            try {
                int g = Integer.parseInt(matcher.group(1));
                int s = -1;
                // 如果有第二段数字，则解析，否则为 -1
                if (matcher.groupCount() >= 2 && matcher.group(2) != null && !matcher.group(2).isEmpty()) {
                    s = Integer.parseInt(matcher.group(2));
                }
                return new HlaInfo(g, s);
            } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    /**
     * 解析抗体字符串，返回“禁止的 Group”列表
     * 输入格式：逗号、顿号、换行、空格分隔
     */
    public static List<Integer> parseAntibodies(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) return Collections.emptyList();

        // 按分隔符拆分
        String[] parts = rawInput.split("[,，\\s\\n]+");
        List<Integer> bannedGroups = new ArrayList<>();

        for (String part : parts) {
            HlaInfo info = parseHla(part);
            if (info != null) {
                // 核心逻辑：只关注第一位数字 (Group)
                bannedGroups.add(info.group);
            }
        }
        return bannedGroups;
    }

    /**
     * 判定是否冲突
     * 规则：只检查第一个数字 (Group)
     */
    public static boolean isConflict(Integer donorGroup, List<Integer> bannedGroups) {
        if (donorGroup == null || bannedGroups == null || bannedGroups.isEmpty()) return false;
        return bannedGroups.contains(donorGroup);
    }

    /**
     * 新版 HLA 打分逻辑
     * 规则：只检查第一个数字 (Group)。如果匹配，+100分；否则 0 分。
     * 不再考虑 +-1 容错。
     */
    public static double calculateSingleScore(HlaInfo target, Integer donorGroup, Integer donorSpecific) {
        if (target == null || donorGroup == null) return 0.0;

        // 核心修改：只比较 Group
        if (target.group == donorGroup) {
            return 100.0;
        }
        return 0.0;
    }

    // 双链计算保留，逻辑依赖 calculateSingleScore
    public static double calculateDualScore(HlaInfo target1, HlaInfo target2,
                                            Integer donorG1, Integer donorS1,
                                            Integer donorG2, Integer donorS2) {
        // (此方法在 Service 中并未直接用于新逻辑的核心判定，但为了兼容性保留)
        // 新逻辑主要在 Service 中手动处理四条链的匹配计数
        return 0.0;
    }

    public static void fillSplitFields(Donor d) {
        if (d == null) return;
        fillOne(d, d.getHlaA1(), "A1");
        fillOne(d, d.getHlaA2(), "A2");
        fillOne(d, d.getHlaB1(), "B1");
        fillOne(d, d.getHlaB2(), "B2");
    }

    private static void fillOne(Donor d, String raw, String type) {
        HlaInfo info = parseHla(raw);
        Integer g = (info != null) ? info.group : null;
        Integer c = (info != null) ? ((info.specific != -1) ? info.specific : null) : null;
        switch (type) {
            case "A1": d.setHlaA1Group(g); d.setHlaA1Code(c); break;
            case "A2": d.setHlaA2Group(g); d.setHlaA2Code(c); break;
            case "B1": d.setHlaB1Group(g); d.setHlaB1Code(c); break;
            case "B2": d.setHlaB2Group(g); d.setHlaB2Code(c); break;
        }
    }
}