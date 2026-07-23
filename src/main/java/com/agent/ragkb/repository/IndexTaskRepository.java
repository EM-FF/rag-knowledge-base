package com.agent.ragkb.repository;

import com.agent.ragkb.entity.IndexTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IndexTaskRepository extends JpaRepository<IndexTask, Long> {
    /**
     * 操作索引任务 index_task
     * ，保存:
     *  任务状态
     *  失败次数
     *  错误信息
     */

    Optional<IndexTask> findTopByDocIdOrderByCreatedAtDesc(Long docId);
}
