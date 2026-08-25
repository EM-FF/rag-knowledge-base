package com.agent.ragkb.service;

import com.agent.ragkb.dto.RagResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 完整 RAG 查询管道（查询改写 + 混合检索 + Reranker + 上下文裁剪 + 生成）。
 * 这是最终版本，后续章节在此基础上添加流式输出、多轮对话等功能。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FullRagPipeline {

    private final EnhancedRetrieverService enhancedRetriever;
    private final RerankerService rerankerService;
    private final ConfidenceFilter confidenceFilter;
    private final ContextTrimmerService contextTrimmer;
    private final org.springframework.ai.chat.client.ChatClient chatClient;

    @Value("${reranker.top-n:5}")
    private int rerankerTopN;

    /**
     * 执行完整 RAG 查询管道。
     *
     * @param question  用户问题
     * @param kbIds     知识库 ID 列表
     * @return 包含答案和来源的结构化响应
     */
    public RagResponse query(String question, List<Long> kbIds) {
        long pipelineStart = System.currentTimeMillis();

        // Step 1：增强检索（混合检索 + HyDE）
        List<HybridRetrieverService.ScoredChunk> candidates =
                enhancedRetriever.retrieveWithHyde(question, kbIds, 20);

        if (candidates.isEmpty()) {
            return RagResponse.notFound();
        }

        // Step 2：Reranker 精排
        List<HybridRetrieverService.ScoredChunk> reranked =
                rerankerService.rerank(question, candidates, rerankerTopN);

        // Step 3：置信度过滤
        List<HybridRetrieverService.ScoredChunk> filtered = confidenceFilter.filter(reranked);

        if (filtered.isEmpty()) {
            return RagResponse.notFound();
        }

        // Step 4：上下文裁剪（控制 Token 预算）
        List<HybridRetrieverService.ScoredChunk> trimmed = contextTrimmer.trim(filtered);

        // Step 5：生成回答
        String context = buildContext(trimmed);
        String answer = generateAnswer(question, context);

        // Step 6：组装来源信息
        List<RagResponse.Source> sources = buildSources(trimmed);

        long elapsed = System.currentTimeMillis() - pipelineStart;
        log.info("[FullRagPipeline] 完成：question={}，elapsed={}ms，sources={}",
                question.substring(0, Math.min(30, question.length())), elapsed, sources.size());

        return RagResponse.builder()
                .answer(answer)
                .sources(sources)
                .latencyMs((int) elapsed)
                .build();
    }

    private String buildContext(List<HybridRetrieverService.ScoredChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            var sc = chunks.get(i);
            sb.append("[参考").append(i + 1).append("]");
            if (sc.chunk().getSectionTitle() != null) {
                sb.append(" ").append(sc.chunk().getSectionTitle());
            }
            sb.append("\n").append(sc.content()).append("\n\n");
        }
        return sb.toString().strip();
    }

    private String generateAnswer(String question, String context) {
        return chatClient.prompt()
                .system("""
                        你是企业内部知识库的智能助手。根据以下参考内容回答用户问题。
                        
                        规则：
                        1. 只基于参考内容回答，不使用自身知识推测
                        2. 参考内容不足时，明确告知"未在知识库找到相关信息"
                        3. 回答用中文，准确简洁，适当列举要点
                        4. 禁止编造参考内容之外的信息
                        
                        参考内容：
                        ---
                        %s
                        ---
                        """.formatted(context))
                .user(question)
                .call()
                .content();
    }

    private List<RagResponse.Source> buildSources(List<HybridRetrieverService.ScoredChunk> chunks) {
        return chunks.stream()
                .map(sc -> RagResponse.Source.builder()
                        .chunkId(sc.id())
                        .docId(sc.chunk().getDocId())
                        .pageNum(sc.chunk().getPageNum())
                        .sectionTitle(sc.chunk().getSectionTitle())
                        .excerpt(sc.content().substring(0, Math.min(200, sc.content().length())))
                        .score(sc.score())
                        .build())
                .collect(Collectors.toList());
    }
}
