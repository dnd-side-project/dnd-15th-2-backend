package com.dnd.qello.filtering.domain;

import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// 모더레이션 정책 구성요소(정규화 규칙·로컬 사전·threshold·model snapshot)를 묶는
// registry 항목. candidate로 시작해 offline evaluation, 비권위 shadow, 비권위
// canary를 거쳐야 승격할 수 있다(INV-REL-006). 각 참조 컬럼은 실제 규칙·threshold
// 값을 담지 않는 opaque 문자열이다 — 이 값이 가리키는 대상의 실제 내용과 합격
// 기준은 별도 설계 게이트에서 다룬다.
//
// latest 같은 alias는 없다(INV-REL-001). 운영 요청은 항상 findCurrentlyPromoted()가
// 돌려주는, 명시적으로 승격된 release만 authoritative로 취급한다.
public record FilterRelease(Long id, String normalizationRef, String localRulesetRef, String categoryMappingRef,
	String modelSnapshot, FilterReleaseStatus status, Instant promotedAt, Instant createdAt) {

	private static final int REF_MAX_LENGTH = 200;
	private static final String LATEST_ALIAS = "latest";

	public FilterRelease {
		if (id != null && id <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "id", "id는 양수여야 합니다");
		}
		normalizationRef = requiredRef(normalizationRef, "normalizationRef");
		localRulesetRef = requiredRef(localRulesetRef, "localRulesetRef");
		categoryMappingRef = requiredRef(categoryMappingRef, "categoryMappingRef");
		modelSnapshot = requiredRef(modelSnapshot, "modelSnapshot");
		if (status == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "status");
		}
		if ((status == FilterReleaseStatus.PROMOTED) != (promotedAt != null)) {
			throw new FilteringException(FilteringErrorCode.INVALID_RELEASE_STATUS, "promotedAt",
				"PROMOTED 상태와 promotedAt은 함께 있어야 합니다");
		}
		if (createdAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "createdAt");
		}
	}

	public static FilterRelease candidate(String normalizationRef, String localRulesetRef,
		String categoryMappingRef, String modelSnapshot, Instant now) {
		return new FilterRelease(null, normalizationRef, localRulesetRef, categoryMappingRef, modelSnapshot,
			FilterReleaseStatus.CANDIDATE, null, now);
	}

	public static FilterRelease restore(Long id, String normalizationRef, String localRulesetRef,
		String categoryMappingRef, String modelSnapshot, FilterReleaseStatus status, Instant promotedAt,
		Instant createdAt) {
		return new FilterRelease(id, normalizationRef, localRulesetRef, categoryMappingRef, modelSnapshot,
			status, promotedAt, createdAt);
	}

	public boolean isAuthoritative() {
		return status == FilterReleaseStatus.PROMOTED;
	}

	public FilterRelease markOfflineEvaluated() {
		requireStatus(FilterReleaseStatus.CANDIDATE, "offline evaluation 완료 처리를");
		return transition(FilterReleaseStatus.OFFLINE_EVALUATED, null);
	}

	public FilterRelease designateShadow() {
		requireStatus(FilterReleaseStatus.OFFLINE_EVALUATED, "shadow 지정을");
		return transition(FilterReleaseStatus.SHADOW, null);
	}

	public FilterRelease designateCanary() {
		requireStatus(FilterReleaseStatus.SHADOW, "canary 지정을");
		return transition(FilterReleaseStatus.CANARY, null);
	}

	// CANARY를 통과한 candidate의 최초 승격. 이미 PROMOTED인 다른 release를
	// 내리는 것은 이 객체의 책임이 아니다 — registry 서비스가 트랜잭션으로 묶는다.
	public FilterRelease promote(Instant now) {
		requireStatus(FilterReleaseStatus.CANARY, "승격을");
		return transition(FilterReleaseStatus.PROMOTED, requireValue(now, "promotedAt"));
	}

	// 예전에 PROMOTED였다가 ROLLED_BACK으로 내려간 release를 다시 승격한다(rollback).
	public FilterRelease rePromote(Instant now) {
		requireStatus(FilterReleaseStatus.ROLLED_BACK, "재승격을");
		return transition(FilterReleaseStatus.PROMOTED, requireValue(now, "promotedAt"));
	}

	// registry 서비스가 다른 release를 승격하기 전에, 현재 PROMOTED release를
	// 내릴 때 호출한다.
	public FilterRelease markRolledBack() {
		requireStatus(FilterReleaseStatus.PROMOTED, "rollback 처리를");
		return transition(FilterReleaseStatus.ROLLED_BACK, null);
	}

	private FilterRelease transition(FilterReleaseStatus nextStatus, Instant nextPromotedAt) {
		return new FilterRelease(id, normalizationRef, localRulesetRef, categoryMappingRef, modelSnapshot,
			nextStatus, nextPromotedAt, createdAt);
	}

	private void requireStatus(FilterReleaseStatus expected, String action) {
		if (status != expected) {
			throw new FilteringException(FilteringErrorCode.INVALID_RELEASE_STATUS, "status",
				status + " 상태에서는 " + action + " 할 수 없습니다");
		}
	}

	private static String requiredRef(String value, String field) {
		if (value == null || value.isBlank() || value.length() > REF_MAX_LENGTH) {
			throw new FilteringException(FilteringErrorCode.INVALID_TEXT, field, field + " 값이 유효하지 않습니다");
		}
		if (LATEST_ALIAS.equalsIgnoreCase(value.trim())) {
			throw new FilteringException(
				FilteringErrorCode.LATEST_ALIAS_NOT_ALLOWED, field, "\"latest\" alias는 사용할 수 없습니다");
		}
		return value;
	}

	private static Instant requireValue(Instant value, String field) {
		if (value == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}
}
