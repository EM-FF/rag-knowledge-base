package com.agent.ragkb.repository;

import com.agent.ragkb.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    List<KnowledgeBase> findByIsDeletedFalse();

    List<KnowledgeBase> findByDepartmentIdAndIsDeletedFalse(String departmentId);

    List<KnowledgeBase> findByIsPublicTrueAndIsDeletedFalse();
}
