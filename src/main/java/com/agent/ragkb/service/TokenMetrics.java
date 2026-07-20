package com.agent.ragkb.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class TokenMetrics {

    /**
     * Embedding 总 Token 数
     */
    private final AtomicLong embeddingTokens = new AtomicLong();

    /**
     * 记录本次 Embedding 消耗的 Token
     */
    public void recordEmbeddingTokens(long tokens) {
        long total = embeddingTokens.addAndGet(tokens);

        log.info("[TokenMetrics] 本次Embedding消耗={}，累计消耗={}",
                tokens, total);
    }

    /**
     * 获取累计 Token
     */
    public long getEmbeddingTokens() {
        return embeddingTokens.get();
    }

    /**
     * 清零
     */
    public void reset() {
        embeddingTokens.set(0);
    }
}
