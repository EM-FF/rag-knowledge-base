package com.agent.ragkb.repository;

import com.agent.ragkb.entity.KbDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface KbDocumentRepository extends JpaRepository<KbDocument, Long> {
    /**
     * 操作文档主表
     * kb_document
     * 保存:
     * 文件信息
     * 状态
     * 版本
      */

    List<KbDocument> findByKbIdAndIsDeletedFalse(Long kbId);

    @Query("SELECT COUNT(d) FROM KbDocument d WHERE d.status = :status")
    long countByStatus(KbDocument.DocumentStatus status);
}