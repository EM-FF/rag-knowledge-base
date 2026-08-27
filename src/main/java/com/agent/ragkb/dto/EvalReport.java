package com.agent.ragkb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvalReport {
    private Long kbId;
    private String evalVersion;
    private long totalQuestions;
    private long hitCount; // 命中数
    private double hitRate; // 命中率
    private double mrr; // 平均倒数排名 越高越好
    private double avgFaithfulness; // 忠实性
    private LocalDateTime evalAt;

    public String summary() {
        return String.format(
                "评估版本：%s | 问题数：%d | Hit Rate：%.1f%% | MRR：%.4f | Faithfulness：%.4f",
                evalVersion, totalQuestions,
                hitRate * 100, mrr, avgFaithfulness);
    }
}
