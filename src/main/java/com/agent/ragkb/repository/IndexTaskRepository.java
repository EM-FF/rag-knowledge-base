package com.agent.ragkb.repository;

import com.agent.ragkb.entity.IndexTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IndexTaskRepository extends JpaRepository<IndexTask, Long> {

    Optional<IndexTask> findTopByDocIdOrderByCreatedAtDesc(Long docId);
}
