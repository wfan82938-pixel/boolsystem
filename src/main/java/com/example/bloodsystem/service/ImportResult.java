package com.example.bloodsystem.service;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ImportResult {
    private int successCount = 0;
    private int failureCount = 0;
    private List<String> errorMessages = new ArrayList<>();

    public void addSuccess(int count) {
        this.successCount += count;
    }

    public void addError(String msg) {
        this.failureCount++;
        // 限制错误日志数量，防止前端页面炸裂
        if (errorMessages.size() < 100) {
            errorMessages.add(msg);
        }
    }

    public List<String> getErrorMessages() {
        return errorMessages;
    }
}