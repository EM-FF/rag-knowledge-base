package com.agent.ragkb.repository;

import com.agent.ragkb.entity.AnswerFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnswerFeedbackRepository extends JpaRepository<AnswerFeedback, Long> {
    Optional<AnswerFeedback> findByMessageIdAndUserId(Long messageId, Long userId);
}