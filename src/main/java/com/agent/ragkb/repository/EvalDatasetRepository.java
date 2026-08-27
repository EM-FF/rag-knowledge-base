package com.agent.ragkb.repository;

import com.agent.ragkb.entity.EvalDataset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvalDatasetRepository extends JpaRepository<EvalDataset, Long> {

    List<EvalDataset> findByKbId(Long kbId);
}
