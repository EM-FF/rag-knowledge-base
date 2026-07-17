package com.agent.ragkb.service.splitter;

import com.agent.ragkb.service.loader.ParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 结构感知分块：优先在标题/段落边界处断开。
 *
 * 适用场景：结构清晰的文档（技术规范、手册等）。
 * 对于流水文字（新闻、小说）效果不如固定窗口。
 *
 * 核心思路：
 * 1. 按标题行切分为若干"节"
 * 2. 节大小 ≤ chunkSize：整节直接作为一块，保留完整语义
 * 3. 节大小 > chunkSize：降级到固定窗口分块器切分
 * （注：节太短不做合并——ChunkService 会统一过滤 < 20 字符的碎片）
 */
@Component("structureAwareSplitter")
@Slf4j
public class StructureAwareChunkSplitter implements ChunkSplitter {

    // 识别中英文标题行：
    //   - "# 标题" / "## 标题" / "### 标题"
    //   - "第X章" / "第X节"
    //   - "一、" / "二、"
    //   - "1." / "1.2" / "1.2.3" 等数字编号，空格可有可无
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^(#{1,3}\\s+|第[一二三四五六七八九十百\\d]+[章节]|[一二三四五六七八九十]+、|\\d+(\\.\\d+)*\\.?\\s*)(.{2,60})$"
    );

    private final SlidingWindowChunkSplitter slidingSplitter;

    public StructureAwareChunkSplitter(SlidingWindowChunkSplitter slidingSplitter) {
        this.slidingSplitter = slidingSplitter;
    }

    @Override
    public List<ChunkResult> split(ParseResult parseResult, ChunkConfig config) {
        // 先按标题边界切分,超大的节再降级到固定窗口
        List<TextSection> sections = extractSections(parseResult);
        List<ChunkResult> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (TextSection section : sections) {
            if (section.text().length() <= config.getChunkSize()) {
                // 节大小 ≤ chunkSize：整节直接作为一块，保留完整语义
                chunks.add(ChunkResult.builder()
                        .chunkIndex(chunkIndex++)
                        .content(section.text())
                        .pageNum(section.pageNum())
                        .sectionTitle(section.title())
                        .estimatedTokens(estimateTokens(section.text()))
                        .build());
            } else {
                // 节太大，降级到固定窗口切分
                ParseResult sectionResult = ParseResult.builder()
                        .success(true)
                        .pages(List.of(ParseResult.PageContent.builder()
                                .pageNum(section.pageNum())
                                .text(section.text())
                                .sectionTitle(section.title())
                                .build()))
                        .totalPages(1)
                        .build();

                List<ChunkResult> subChunks = slidingSplitter.split(sectionResult, config);
                for (ChunkResult sub : subChunks) {
                    sub.setChunkIndex(chunkIndex++);
                    if (sub.getSectionTitle() == null) sub.setSectionTitle(section.title());
                    chunks.add(sub);
                }
            }
        }

        return chunks;
    }

    private List<TextSection> extractSections(ParseResult parseResult) {
        List<TextSection> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentTitle = null;
        int sectionStartPage = 1;   // ★ 当前节的"起始页"——用于引用溯源时给用户翻到对的位置

        for (ParseResult.PageContent page : parseResult.getPages()) {
            // 如果解析层已经识别出 sectionTitle，直接作为当前节的标题（覆盖跨页的延续）
            if (page.getSectionTitle() != null && current.length() == 0) {
                currentTitle = page.getSectionTitle();
                sectionStartPage = page.getPageNum();   // 新节起始页 = 当前页
            }

            String[] lines = page.getText().split("\n");
            for (String line : lines) {
                String stripped = line.strip();
                boolean isHeading = !stripped.isEmpty() && HEADING_PATTERN.matcher(stripped).matches();

                if (isHeading) {
                    // 遇到标题——先把已经积累的当前节保存掉，再切换到新标题
                    if (current.length() > 50) {
                        sections.add(new TextSection(currentTitle, current.toString().strip(), sectionStartPage));
                        current = new StringBuilder();
                        sectionStartPage = page.getPageNum();   // ★ 新节起始页 = 标题所在页
                    }
                    // 新节的 title 用这一行；标题行本身不再 append 到 current，避免 Embedding 时重复
                    currentTitle = stripped;
                    continue;
                }

                current.append(line).append("\n");
            }
        }

        if (!current.isEmpty()) {
            sections.add(new TextSection(currentTitle, current.toString().strip(), sectionStartPage));
        }

        return sections;
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        int chinese = 0, other = 0;
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') chinese++;
            else if (!Character.isWhitespace(c)) other++;
        }
        return (int) (chinese * 1.5 + other * 0.3);
    }

    record TextSection(String title, String text, int pageNum) {}
}
