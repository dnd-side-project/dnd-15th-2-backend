package com.dnd.qello.filtering.repository.jpa;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.filtering.domain.ReleasePromotionHistoryEntry;
import com.dnd.qello.filtering.repository.ReleasePromotionHistoryRepository;

@Repository
@Transactional(readOnly = true)
public class JpaReleasePromotionHistoryRepository implements ReleasePromotionHistoryRepository {

	private final SpringDataReleasePromotionHistoryRepository repository;

	public JpaReleasePromotionHistoryRepository(SpringDataReleasePromotionHistoryRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public ReleasePromotionHistoryEntry save(ReleasePromotionHistoryEntry entry) {
		return ReleasePromotionHistoryJpaMapper.toDomain(
			repository.saveAndFlush(new ReleasePromotionHistoryJpaEntity(entry)));
	}

	@Override
	public List<ReleasePromotionHistoryEntry> findByReleaseId(long releaseId) {
		return repository.findByReleaseId(releaseId).stream()
			.map(ReleasePromotionHistoryJpaMapper::toDomain)
			.toList();
	}
}
