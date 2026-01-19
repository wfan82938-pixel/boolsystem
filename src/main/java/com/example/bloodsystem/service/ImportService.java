package com.example.bloodsystem.service;

import com.example.bloodsystem.entity.Donor;
import com.example.bloodsystem.repository.DonorRepository;
import com.example.bloodsystem.util.HlaUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.*;
import java.util.*;

@Service
public class ImportService {
    @Autowired private DonorRepository repository;
    @Autowired private TransactionTemplate transactionTemplate;

    // 批量保存的大小
    private static final int BATCH_SIZE = 1000;

    public ImportResult parseAndImportText(String textData) {
        ImportResult result = new ImportResult();
        if (textData == null || textData.trim().isEmpty()) return result;

        List<Donor> batchList = new ArrayList<>(BATCH_SIZE);

        try (BufferedReader reader = new BufferedReader(new StringReader(textData))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                // 仅去除首尾空白，保留行内 Tab 结构
                line = line.trim();

                if (line.isEmpty() || isHeader(line)) continue;

                Donor donor = parseLine(line);
                if (donor != null) {
                    batchList.add(donor);
                } else {
                    result.addError("第 " + lineNum + " 行格式错误或数据不足");
                }

                if (batchList.size() >= BATCH_SIZE) {
                    saveBatch(batchList, result);
                    batchList.clear();
                }
            }
            if (!batchList.isEmpty()) saveBatch(batchList, result);
        } catch (Exception e) {
            e.printStackTrace();
            result.addError("系统错误: " + e.getMessage());
        }
        return result;
    }

    private void saveBatch(List<Donor> donors, ImportResult result) {
        if(donors.isEmpty()) return;
        try {
            transactionTemplate.execute(status -> {
                repository.saveAll(donors);
                repository.flush();
                return null;
            });
            result.addSuccess(donors.size());
        } catch (Exception e) {
            result.addError("批量保存失败: " + e.getMessage());
        }
    }

    private boolean isHeader(String line) {
        // 简单判断是否为标题行
        return line.toUpperCase().startsWith("NO") ||
                line.toUpperCase().contains("姓名") ||
                (line.toUpperCase().contains("ID") && line.length() < 50);
    }

    /**
     * 安全获取数组元素
     * @param arr 数组
     * @param idx 索引
     * @return 清洗后的值，如果为空或"-"则返回 null
     */
    private String safeGet(String[] arr, int idx) {
        if (arr == null || idx >= arr.length) return null;

        String v = arr[idx].trim(); // 这里去除单元格内的多余空格

        // 处理常见的无效字符
        if (v.isEmpty() || v.equals("-") || v.equalsIgnoreCase("null") || v.equals("/")) {
            return null;
        }
        return v;
//        return v.toLowerCase();
    }

    private Donor parseLine(String line) {
        try {
            // 🔥 核心修复 1：使用 split("\t", -1) 防止空列导致的数据错位
            // Excel 复制出来的数据严格以 Tab 分隔。
            // 之前的 split("\\s+") 会把 "空ID" 的两个 Tab 合并，导致后续列前移。
            // -1 参数确保 "a\t\tb" 被拆分为 ["a", "", "b"] 而不是 ["a", "b"]
            String[] parts = line.split("\t", -1);

            // 简单校验列数，至少要有姓名(0)和一部分基因数据，防止空行干扰
            // 这里的长度判断取决于你的 Excel 模板最少有多少列
            if (parts.length < 5) return null;

            Donor d = new Donor();

            // 索引 0: 姓名
            d.setName(safeGet(parts, 0));

            // 索引 1: ID
            String oid = safeGet(parts, 1);
            d.setDonorId(oid == null ? UUID.randomUUID().toString().replace("-", "").substring(0, 10) : oid);

            // 默认血型
            d.setBloodType("未知");

            // 索引 2-10: HPA 1-21
            d.setHpa1(safeGet(parts, 2));
            d.setHpa2(safeGet(parts, 3));
            d.setHpa3(safeGet(parts, 4));
            d.setHpa4(safeGet(parts, 5));
            d.setHpa5(safeGet(parts, 6));
            d.setHpa6(safeGet(parts, 7));
            d.setHpa10(safeGet(parts, 8));
            d.setHpa15(safeGet(parts, 9));
            d.setHpa21(safeGet(parts, 10));

            // 索引 11-14: HLA (可能存在越界风险，safeGet 会处理)
            d.setHlaA1(safeGet(parts, 11));
            d.setHlaA2(safeGet(parts, 12));
            d.setHlaB1(safeGet(parts, 13));
            d.setHlaB2(safeGet(parts, 14));

            // 🔥 核心修复 2：确保解析 HLA 字符串并填充数字字段 (Group/Code)
            // 如果不调用这个，数据库里用于搜索的数字字段(hla_a1_group等)将是 null，导致配型搜不到人
            HlaUtils.fillSplitFields(d);

            return d;
        } catch (Exception e) {
            // 解析单行失败不应中断整个流程
            return null;
        }
    }
}