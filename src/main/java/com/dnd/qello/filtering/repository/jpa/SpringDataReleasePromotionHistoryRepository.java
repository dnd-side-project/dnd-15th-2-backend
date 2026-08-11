package com.dnd.qello.filtering.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataReleasePromotionHistoryRepository extends JpaRepository<ReleasePromotionHistoryJpaEntity, Long> {

	List<ReleasePromotionHistoryJpaEntity> findByReleaseId(long releaseId);
}
