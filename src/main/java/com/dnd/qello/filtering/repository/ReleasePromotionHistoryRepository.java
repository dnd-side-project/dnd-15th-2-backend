package com.dnd.qello.filtering.repository;

import java.util.List;

import com.dnd.qello.filtering.domain.ReleasePromotionHistoryEntry;

public interface ReleasePromotionHistoryRepository {

	ReleasePromotionHistoryEntry save(ReleasePromotionHistoryEntry entry);

	List<ReleasePromotionHistoryEntry> findByReleaseId(long releaseId);
}
