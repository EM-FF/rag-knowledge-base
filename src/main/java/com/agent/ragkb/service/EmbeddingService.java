package com.agent.ragkb.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Service
@Slf4j
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final StringRedisTemplate redisTemplate;
    private final TokenMetrics tokenMetrics;

    /**
     * 自身代理引用——用于内部调用走 AOP 代理（@Retryable / @Recover 才会生效）。
     * 必须用 @Lazy，否则会形成构造期循环依赖（自己构造时拿不到自己）。
     */
    private final EmbeddingService self;

    public EmbeddingService(EmbeddingModel embeddingModel,
                            StringRedisTemplate redisTemplate,
                            TokenMetrics tokenMetrics,
                            @Lazy EmbeddingService self) {
        this.embeddingModel = embeddingModel;
        this.redisTemplate = redisTemplate;
        this.tokenMetrics = tokenMetrics;
        this.self = self;
    }

    private static final String CACHE_PREFIX = "emb:v1:";

    @Value("${rag.cache.embedding-ttl:7d}")
    private Duration embeddingTtl;

    private static final int BATCH_SIZE = 20;

    /**
     * 批量向量化，带 Redis 缓存。
     * 先查Redis缓存，缓存未命中的批量调 API，结果写入缓存。
     *
     * @param texts 待向量化的文本列表
     * @return 与输入顺序对应的向量列表
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();

        Map<Integer, float[]> cached = new HashMap<>();
        List<Integer> missedIndices = new ArrayList<>();
        List<String> missedTexts = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            String cacheKey = buildCacheKey(texts.get(i));
            String cachedStr = redisTemplate.opsForValue().get(cacheKey);
            if (cachedStr != null) {
                try {
                    cached.put(i, deserializeVector(cachedStr));
                } catch (NumberFormatException ex) {
                    // 缓存数据被污染（旧版本残留 / 人工写入 / 截断等）
                    // 当作缓存 miss 处理：清掉脏 key、走 API 重新算
                    log.warn("[Embedding] 缓存数据损坏，回退到 API：key={}, err={}",
                            cacheKey, ex.getMessage());
                    redisTemplate.delete(cacheKey);
                    missedIndices.add(i);
                    missedTexts.add(texts.get(i));
                }
            } else {
                missedIndices.add(i);
                missedTexts.add(texts.get(i));
            }
        }

        log.debug("[Embedding] 总数={}，缓存命中={}，需要调API={}",
                texts.size(), cached.size(), missedTexts.size());

        if (!missedTexts.isEmpty()) {
            // ★ 关键：必须用 self.embedFromApi(...) 走代理，否则 @Retryable / @Recover 不生效
            //   直接 this.embedFromApi(...) 会绕过 Spring AOP 代理 —— 重试/兜底全失效
            List<float[]> newVectors = self.embedFromApi(missedTexts);

            for (int j = 0; j < missedIndices.size(); j++) {
                int originalIndex = missedIndices.get(j);
                float[] vector = newVectors.get(j);
                cached.put(originalIndex, vector);

                String cacheKey = buildCacheKey(texts.get(originalIndex));
                redisTemplate.opsForValue().set(cacheKey, serializeVector(vector), embeddingTtl);
            }
        }

        return IntStream.range(0, texts.size())
                .mapToObj(cached::get)
                .toList();
    }

    /**
     * 调 Embedding API，按批次处理，避免单次请求过大。
     *
     * 带重试：网络抖动 / 5xx / 限流 时自动重试 3 次，指数退避。
     * 不重试：4xx 类客户端错（API Key 错、参数错、文本超长）—— 重试 100 次结果一样，纯浪费时间和 Token。
     */
    @Retryable(
            retryFor = {
                    ResourceAccessException.class,        // 网络 IO 异常（连接超时、读超时、Connection reset）
                    HttpServerErrorException.class,        // 5xx 服务端错误（502/503/504）
                    java.util.concurrent.TimeoutException.class
            },
            noRetryFor = HttpClientErrorException.class,   // 4xx 不重试（401/400/413）
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<float[]> embedFromApi(List<String> texts) {
        List<float[]> result = new ArrayList<>();
        AtomicInteger totalTokens = new AtomicInteger(0);

        // 分批提交
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(start, end);

            long batchStart = System.currentTimeMillis();
            EmbeddingResponse response = embeddingModel.call(
                    new EmbeddingRequest(batch, null));
            long elapsed = System.currentTimeMillis() - batchStart;

            // 统计 Token 消耗（用于成本监控）
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                long tokens = response.getMetadata().getUsage().getTotalTokens();
                totalTokens.addAndGet((int) tokens);
            }

            // 按顺序提取向量
            // Spring AI 1.1.x：Embedding.getOutput() 返回 float[]
            response.getResults().stream()
                    .sorted(Comparator.comparingInt(r -> r.getIndex()))
                    .forEach(r -> result.add(r.getOutput()));

            log.debug("[Embedding] 批次{}/{}，size={}，耗时={}ms",
                    start / BATCH_SIZE + 1,
                    (texts.size() + BATCH_SIZE - 1) / BATCH_SIZE,
                    batch.size(), elapsed);
        }

        log.info("[Embedding] API调用完成，共{}条，消耗Token={}",
                texts.size(), totalTokens.get());

        if (totalTokens.get() > 0) {
            tokenMetrics.recordEmbeddingTokens(totalTokens.get());
        }

        return result;
    }

    /**
     * 兜底方法 —— 重试 3 次后仍失败时进入这里（仅对 retryFor 中的异常生效）。
     *
     * 注意：4xx 客户端错（HttpClientErrorException）已通过 noRetryFor 排除——
     * 这类异常不会进入重试链路、也不会触发 @Recover，会直接冒泡给调用方。
     */
    @Recover
    public List<float[]> embedFromApiFallback(Exception e, List<String> texts) {
        log.error("[Embedding] 重试3次后仍失败，texts.size={}，error={}",
                texts.size(), e.getMessage());
        throw new RuntimeException("Embedding API 调用失败，已重试3次：" + e.getMessage(), e);
    }

    /** 单条向量化（查询时使用） */
    public float[] embed(String text) {
        List<float[]> result = embedBatch(List.of(text));
        return result.isEmpty() ? new float[0] : result.get(0);
    }

    private String buildCacheKey(String text) {
        // 用内容的 MD5 作为缓存 Key，避免 Key 过长
        return CACHE_PREFIX + toMd5(text);
    }

    private String toMd5(String text) {
        try {
            var md = java.security.MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }

    /**
     * float[] 转逗号分隔字符串存入 Redis。
     * 不用 JSON 序列化器，避免 GenericJackson2JsonRedisSerializer
     * 把浮点数当类名解析导致反序列化失败。
     */
    private String serializeVector(float[] vector) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    /**
     *带逗号分隔符的 向量字符串 转float[]
     */
    private float[] deserializeVector(String str) {
        str = str.replace("[", "").replace("]", "").replace(" ", "");
        String[] parts = str.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }

}