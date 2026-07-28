package com.agent.ragkb.repository;

import com.agent.ragkb.entity.KbPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KbPermissionRepository extends JpaRepository<KbPermission, Long> {

    List<KbPermission> findBySubjectTypeAndSubjectId(String subjectType, String subjectId);

    boolean existsByKbIdAndSubjectTypeAndSubjectId(
            Long kbId, String subjectType, String subjectId);

    boolean existsByKbIdAndSubjectTypeAndSubjectIdAndPermissionIn(
            Long kbId, String subjectType, String subjectId, List<String> permissions);

    List<KbPermission> findByKbId(Long kbId);

    void deleteByKbId(Long kbId);
}