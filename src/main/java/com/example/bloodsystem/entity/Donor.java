package com.example.bloodsystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
@Entity
@Table(name = "donors", indexes = {
        @Index(name = "idx_blood_type", columnList = "blood_type"), // 修正为数据库列名
        @Index(name = "idx_donor_id", columnList = "donor_id"),     // 修正为数据库列名
        @Index(name = "idx_name", columnList = "name"),
        // HPA 索引
        @Index(name = "idx_hpa1", columnList = "hpa1"),
        @Index(name = "idx_hpa2", columnList = "hpa2"),
        @Index(name = "idx_hpa3", columnList = "hpa3"),
        @Index(name = "idx_hpa4", columnList = "hpa4"),
        @Index(name = "idx_hpa5", columnList = "hpa5"),
        @Index(name = "idx_hpa6", columnList = "hpa6"),
        @Index(name = "idx_hpa10", columnList = "hpa10"),
        @Index(name = "idx_hpa15", columnList = "hpa15"),
        @Index(name = "idx_hpa21", columnList = "hpa21"),
        // 🔥🔥 修正：索引必须指向 @Column 中定义的数据库列名 (snake_case)
        @Index(name = "idx_hla_a1_g", columnList = "hla_a1_group"),
        @Index(name = "idx_hla_a2_g", columnList = "hla_a2_group"),
        @Index(name = "idx_hla_b1_g", columnList = "hla_b1_group"),
        @Index(name = "idx_hla_b2_g", columnList = "hla_b2_group")
})
public class Donor {
    @Id
    @Column(length = 50, name = "donor_id") // 显式指定
    private String donorId;

    @Version
    private Integer version;

    private String name;
    private String gender;
    private Integer age;

    @Column(length = 10, name = "blood_type") // 显式指定为 blood_type
    private String bloodType;

    private String phone;

    // --- HPA 基因型 (短名称一般不自动加下划线，保持原样即可，也可以显式加上) ---
    @Column(length = 10) private String hpa1;
    @Column(length = 10) private String hpa2;
    @Column(length = 10) private String hpa3;
    @Column(length = 10) private String hpa4;
    @Column(length = 10) private String hpa5;
    @Column(length = 10) private String hpa6;
    @Column(length = 10) private String hpa10;
    @Column(length = 10) private String hpa15;
    @Column(length = 10) private String hpa21;

    // --- HLA 原始字符串 (用于前端展示) ---
    @Column(length = 20, name = "hla_a1") private String hlaA1;
    @Column(length = 20, name = "hla_a2") private String hlaA2;
    @Column(length = 20, name = "hla_b1") private String hlaB1;
    @Column(length = 20, name = "hla_b2") private String hlaB2;

    // --- 🔥🔥 核心修复：显式指定数据库列名为下划线格式 ---

    @Column(name = "hla_a1_group")
    private Integer hlaA1Group;

    @Column(name = "hla_a1_code")
    private Integer hlaA1Code;

    @Column(name = "hla_a2_group")
    private Integer hlaA2Group;

    @Column(name = "hla_a2_code")
    private Integer hlaA2Code;

    @Column(name = "hla_b1_group")
    private Integer hlaB1Group;

    @Column(name = "hla_b1_code")
    private Integer hlaB1Code;

    @Column(name = "hla_b2_group")
    private Integer hlaB2Group;

    @Column(name = "hla_b2_code")
    private Integer hlaB2Code;

    // --- 辅助方法 ---

    public Map<String, String> getGenotypesMap() {
        Map<String, String> map = new HashMap<>();
        if (hpa1 != null) map.put("HPA-1", hpa1);
        if (hpa2 != null) map.put("HPA-2", hpa2);
        if (hpa3 != null) map.put("HPA-3", hpa3);
        if (hpa4 != null) map.put("HPA-4", hpa4);
        if (hpa5 != null) map.put("HPA-5", hpa5);
        if (hpa6 != null) map.put("HPA-6", hpa6);
        if (hpa10 != null) map.put("HPA-10", hpa10);
        if (hpa15 != null) map.put("HPA-15", hpa15);
        if (hpa21 != null) map.put("HPA-21", hpa21);
        if (hlaA1 != null) map.put("HLA-A", hlaA1);
        return map;
    }

    public String getGenotype(String locus) {
        if (locus == null) return null;
        switch (locus.toUpperCase()) {
            case "HPA-1": return hpa1;
            case "HPA-2": return hpa2;
            case "HPA-3": return hpa3;
            case "HPA-4": return hpa4;
            case "HPA-5": return hpa5;
            case "HPA-6": return hpa6;
            case "HPA-10": return hpa10;
            case "HPA-15": return hpa15;
            case "HPA-21": return hpa21;
            case "HLA-A1": return hlaA1;
            case "HLA-A2": return hlaA2;
            case "HLA-B1": return hlaB1;
            case "HLA-B2": return hlaB2;
            default: return null;
        }
    }
}