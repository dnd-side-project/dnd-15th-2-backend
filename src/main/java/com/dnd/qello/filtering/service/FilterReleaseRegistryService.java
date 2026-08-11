package com.dnd.qello.filtering.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.ReleasePromotionAction;
import com.dnd.qello.filtering.domain.ReleasePromotionHistoryEntry;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;
import com.dnd.qello.filtering.repository.ReleasePromotionHistoryRepository;

// moderation release registry의 유일한 쓰기 진입점. candidate 생성부터 승격·rollback까지
// 모든 상태 전이가 이 서비스를 거친다 — alias나 다른 자동 경로는 없다(INV-REL-001,
// INV-REL-008).
@Service
@Transactional(readOnly = true)
public class FilterReleaseRegistryService {

	private final FilterReleaseRepository filterReleaseRepository;
	private final ReleasePromotionHistoryRepository promotionHistoryRepository;
	private final Clock clock;

	public FilterReleaseRegistryService(
		FilterReleaseRepository filterReleaseRepository,
		ReleasePromotionHistoryRepository promotionHistoryRepository,
		Clock clock
	) {
		this.filterReleaseRepository = filterReleaseRepository;
		this.promotionHistoryRepository = promotionHistoryRepository;
		this.clock = clock;
	}

	@Transactional
	public FilterRelease createCandidate(
		String normalizationRef, String localRulesetRef, String categoryMappingRef, String modelSnapshot
	) {
		FilterRelease candidate = FilterRelease.candidate(
			normalizationRef, localRulesetRef, categoryMappingRef, modelSnapshot, Instant.now(clock));
		return filterReleaseRepository.save(candidate);
	}

	public FilterRelease find(long releaseId) {
		return filterReleaseRepository.findById(releaseId)
			.orElseThrow(() -> new FilteringException(FilteringErrorCode.RELEASE_NOT_FOUND, "releaseId"));
	}

	public List<FilterRelease> findAll() {
		return filterReleaseRepository.findAll();
	}

	@Transactional
	public FilterRelease markOfflineEvaluated(long releaseId) {
		return filterReleaseRepository.save(find(releaseId).markOfflineEvaluated());
	}

	@Transactional
	public FilterRelease designateShadow(long releaseId) {
		return filterReleaseRepository.save(find(releaseId).designateShadow());
	}

	@Transactional
	public FilterRelease designateCanary(long releaseId) {
		return filterReleaseRepository.save(find(releaseId).designateCanary());
	}

	// CANARY를 통과한 release의 명시적 승격. 기존에 PROMOTED인 release가 있으면 먼저
	// ROLLED_BACK으로 내린 뒤에만 새 release를 올린다 — 두 release가 동시에 PROMOTED인
	// 순간은 없다(DB의 uq_filter_release_single_promoted가 이를 다시 강제한다).
	@Transactional
	public FilterRelease promote(long releaseId, long operatorUserId) {
		Instant now = Instant.now(clock);
		FilterRelease target = find(releaseId);
		Long previousActiveReleaseId = demoteCurrentlyPromoted(releaseId, now);

		FilterRelease promoted = filterReleaseRepository.save(target.promote(now));
		promotionHistoryRepository.save(ReleasePromotionHistoryEntry.of(
			promoted.id(), ReleasePromotionAction.PROMOTE, previousActiveReleaseId, operatorUserId, now));
		return promoted;
	}

	// 이전에 PROMOTED였다가 ROLLED_BACK으로 내려간 release를 다시 승격한다.
	@Transactional
	public FilterRelease rollback(long releaseId, long operatorUserId) {
		Instant now = Instant.now(clock);
		FilterRelease target = find(releaseId);
		Long previousActiveReleaseId = demoteCurrentlyPromoted(releaseId, now);

		FilterRelease rePromoted = filterReleaseRepository.save(target.rePromote(now));
		promotionHistoryRepository.save(ReleasePromotionHistoryEntry.of(
			rePromoted.id(), ReleasePromotionAction.ROLLBACK, previousActiveReleaseId, operatorUserId, now));
		return rePromoted;
	}

	private Long demoteCurrentlyPromoted(long targetReleaseId, Instant now) {
		Optional<FilterRelease> currentlyPromoted = filterReleaseRepository.findCurrentlyPromoted();
		if (currentlyPromoted.isEmpty()) {
			return null;
		}
		FilterRelease active = currentlyPromoted.get();
		if (active.id().equals(targetReleaseId)) {
			throw new FilteringException(FilteringErrorCode.INVALID_RELEASE_STATUS, "releaseId",
				"이미 승격된 release입니다");
		}
		filterReleaseRepository.save(active.markRolledBack());
		return active.id();
	}
}
