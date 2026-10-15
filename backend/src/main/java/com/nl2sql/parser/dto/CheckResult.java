package com.nl2sql.parser.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 校验结果
 * status: PASS 通过 / WARNING 警告(可执行但有风险) / REJECT 拒绝
 * fixedSql: 自动修复后的 SQL(可能与原 SQL 相同)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckResult {
    public enum Status { PASS, WARNING, REJECT }
    private Status status;
    private List<String> issues = new ArrayList<>();
    private String fixedSql;
    private long checkCostMs;
    private String reason; // 仅 REJECT 时使用

    public static CheckResult pass(String fixed, long costMs) {
        return new CheckResult(Status.PASS, new ArrayList<>(), fixed, costMs, null);
    }
    public static CheckResult warn(List<String> issues, String fixed, long costMs) {
        return new CheckResult(Status.WARNING, issues, fixed, costMs, null);
    }
    public static CheckResult reject(String reason, long costMs) {
        return new CheckResult(Status.REJECT, new ArrayList<>(), null, costMs, reason);
    }
}
