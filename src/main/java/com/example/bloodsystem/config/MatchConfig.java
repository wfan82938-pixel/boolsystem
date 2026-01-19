package com.example.bloodsystem.config;

import org.springframework.context.annotation.Configuration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
public class MatchConfig {

    private final List<String> allHpas = List.of(
            "HPA-1", "HPA-2", "HPA-3", "HPA-4", "HPA-5", "HPA-6", "HPA-10", "HPA-15", "HPA-21"
    );

    private final Set<String> criticalHpas = Set.of("HPA-1", "HPA-5", "HPA-15");

    private final Map<String, Double> weights = new HashMap<>();
    private final Map<String, Map<String, Integer>> compatMatrix = new HashMap<>();

    public MatchConfig() {
        // HPA 权重配置 (虽然现在 Service 里写死了+5/+2，但保留此配置结构)
        allHpas.forEach(h -> weights.put(h, 1.0));

        // HPA 兼容性矩阵 (0=不匹配, 1=兼容, 2=匹配)
        compatMatrix.put("aa", Map.of("aa", 2, "ab", 1, "bb", 0));
        compatMatrix.put("ab", Map.of("aa", 1, "ab", 2, "bb", 1));
        compatMatrix.put("bb", Map.of("aa", 0, "ab", 1, "bb", 2));
    }

    public List<String> getAllHpas() { return allHpas; }
    public Set<String> getCriticalHpas() { return criticalHpas; }

    public double getWeight(String locus) {
        return weights.getOrDefault(locus, 1.0);
    }

    public int getScore(String pVal, String dVal) {
        if (pVal == null || dVal == null) return 0;
        return compatMatrix.getOrDefault(pVal, Map.of()).getOrDefault(dVal, 0);
    }
}