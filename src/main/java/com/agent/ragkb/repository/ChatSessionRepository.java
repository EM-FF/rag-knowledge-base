package com.agent.ragkb.repository;

import com.agent.ragkb.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    List<ChatSession> findByUserIdAndIsDeletedFalseOrderByLastActiveAtDesc(Long userId);
}

