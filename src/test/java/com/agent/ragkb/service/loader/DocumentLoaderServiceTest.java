package com.agent.ragkb.service.loader;

import com.agent.ragkb.service.DocumentLoaderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DocumentLoaderServiceTest {

    @Autowired
    private DocumentLoaderService loaderService;

    private String extractText(ParseResult result) {
        return result.getPages().stream()
                .map(ParseResult.PageContent::getText)
                .collect(Collectors.joining("\n"));
    }

    @Test
    void parseTxtFile() throws Exception {
        ClassPathResource resource = new ClassPathResource("test-docs/hr-handbook.txt");
        try (InputStream is = resource.getInputStream()) {
            ParseResult result = loaderService.load(is, "hr-handbook.txt");
            String text = extractText(result);

            System.out.println("解析文本长度：" + text.length());
            System.out.println("解析内容前100字：" + text.substring(0, Math.min(100, text.length())));

            assertThat(result.isSuccess()).isTrue();
            assertThat(text).isNotBlank();
            assertThat(text).contains("员工手册");
            assertThat(text).contains("入职流程");
            assertThat(text).contains("假期制度");
        }
    }

    @Test
    void parsePdf() throws Exception {
        ClassPathResource resource = new ClassPathResource("test-docs/product-faq.pdf");
        try (InputStream is = resource.getInputStream()) {
            ParseResult result = loaderService.load(is, "product-faq.pdf");
            String text = extractText(result);

            System.out.println("解析文本长度：" + text.length());
            System.out.println("解析内容前100字：" + text.substring(0, Math.min(100, text.length())));

            assertThat(result.isSuccess()).isTrue();
            assertThat(text).isNotBlank();
            assertThat(result.getTotalPages()).isGreaterThan(0);
        }
    }

    @Test
    void parseDocx() throws Exception {
        ClassPathResource resource = new ClassPathResource("test-docs/product-faq.docx");
        try (InputStream is = resource.getInputStream()) {
            ParseResult result = loaderService.load(is, "product-faq.docx");
            String text = extractText(result);

            System.out.println("解析文本长度：" + text.length());
            System.out.println("分节数：" + result.getPages().size());

            assertThat(result.isSuccess()).isTrue();
            assertThat(text).isNotBlank();
            assertThat(result.getPages()).isNotEmpty();
        }
    }

    @Test
    void parseMd() throws Exception {
        ClassPathResource resource = new ClassPathResource("test-docs/hr-handbook.md");
        try (InputStream is = resource.getInputStream()) {
            ParseResult result = loaderService.load(is, "hr-handbook.md");
            String text = extractText(result);

            System.out.println("解析文本长度：" + text.length());
            System.out.println("解析内容前100字：" + text.substring(0, Math.min(100, text.length())));

            assertThat(result.isSuccess()).isTrue();
            assertThat(text).isNotBlank();
            assertThat(result.getPages()).isNotEmpty();
        }
    }

    @Test
    void unsupportedTypeReturnsFailure() {
        InputStream emptyStream = InputStream.nullInputStream();
        ParseResult result = loaderService.load(emptyStream, "test.xyz");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("不支持的文件类型");
    }
}