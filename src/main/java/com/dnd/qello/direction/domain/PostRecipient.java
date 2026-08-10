package com.dnd.qello.direction.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

import lombok.Getter;

@Getter
public final class PostRecipient {

	private final Long id;
	private final Long postId;
	private final Long recipientId;
	private final PostRecipientStatus status;
	private final String distanceBand;
	private final BigDecimal matchedBearingDegrees;
	private final String matchedRegionCode;
	private final Instant matchedAt;
	private final Instant discoveredAt;
	private final Instant openedAt;
	private final Instant skipRequestedAt;
	private final Instant skippedAt;
	private final Instant capacityReleasedAt;
	private final Instant expiredAt;
	private final Instant blockedAt;
	private final BigDecimal inboundBearingDegrees;
	private final long distanceM;
	private final Instant answersReadAt;

	private PostRecipient(Long id, Long postId, Long recipientId, PostRecipientStatus status,
		String distanceBand, BigDecimal matchedBearingDegrees, String matchedRegionCode,
		Instant matchedAt, Instant discoveredAt, Instant openedAt, Instant skipRequestedAt, Instant skippedAt,
		Instant capacityReleasedAt, Instant expiredAt, Instant blockedAt,
		BigDecimal inboundBearingDegrees, long distanceM, Instant answersReadAt) {
		this.id = id;
		this.postId = requireId(postId, "postId");
		this.recipientId = requireId(recipientId, "recipientId");
		this.status = requireValue(status, "status");
		this.distanceBand = requireText(distanceBand, "distanceBand", 50);
		this.matchedBearingDegrees = requireValue(matchedBearingDegrees, "matchedBearingDegrees");
		if (matchedBearingDegrees.signum() < 0 || matchedBearingDegrees.compareTo(BigDecimal.valueOf(360)) >= 0) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_BEARING, "matchedBearingDegrees", "matchedBearingDegrees는 [0, 360)이어야 합니다");
		}
		this.matchedRegionCode = requireText(matchedRegionCode, "matchedRegionCode", 100);
		this.matchedAt = requireValue(matchedAt, "matchedAt");
		this.discoveredAt = discoveredAt;
		this.openedAt = openedAt;
		this.skipRequestedAt = skipRequestedAt;
		this.skippedAt = skippedAt;
		this.capacityReleasedAt = capacityReleasedAt;
		this.expiredAt = expiredAt;
		this.blockedAt = blockedAt;
		this.inboundBearingDegrees = requireValue(inboundBearingDegrees, "inboundBearingDegrees");
		if (inboundBearingDegrees.signum() < 0 || inboundBearingDegrees.compareTo(BigDecimal.valueOf(360)) >= 0) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_BEARING, "inboundBearingDegrees", "inboundBearingDegrees는 [0, 360)이어야 합니다");
		}
		if (distanceM < 0) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_VALUE_RANGE, "distanceM", "distanceM은 음수일 수 없습니다");
		}
		this.distanceM = distanceM;
		this.answersReadAt = answersReadAt;
		validateTimestamp(discoveredAt, "discoveredAt");
		validateTimestamp(openedAt, "openedAt");
		validateTimestamp(skipRequestedAt, "skipRequestedAt");
		validateTimestamp(skippedAt, "skippedAt");
		validateTimestamp(capacityReleasedAt, "capacityReleasedAt");
		validateTimestamp(expiredAt, "expiredAt");
		validateTimestamp(blockedAt, "blockedAt");
		validateTimestamp(answersReadAt, "answersReadAt");
		if ((status == PostRecipientStatus.SKIP_PENDING) != (skipRequestedAt != null && skippedAt == null)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE, "status", "SKIP_PENDING은 넘김 요청만 있고 확정이 없는 상태여야 합니다");
		}
		if (skipRequestedAt == null && skippedAt != null) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE, "skipRequestedAt", "확정된 넘김에는 skipRequestedAt이 필요합니다");
		}
		if (skippedAt != null && skippedAt.isBefore(skipRequestedAt)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_TIME_ORDER, "skippedAt", "skippedAt은 skipRequestedAt보다 빠를 수 없습니다");
		}
		if ((status == PostRecipientStatus.SKIPPED) != (skippedAt != null)
			|| (status == PostRecipientStatus.EXPIRED) != (expiredAt != null)
			|| (status == PostRecipientStatus.BLOCKED) != (blockedAt != null)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE, "status", "terminal status와 timestamp가 일치하지 않습니다");
		}
		if ((status == PostRecipientStatus.DISCOVERED || status == PostRecipientStatus.OPENED || status == PostRecipientStatus.ANSWERED)
			&& discoveredAt == null) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE, "discoveredAt", "DISCOVERED 이후에는 discoveredAt이 필요합니다");
		}
		if ((status == PostRecipientStatus.OPENED || status == PostRecipientStatus.ANSWERED) && openedAt == null) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE, "openedAt", "OPENED 이후에는 openedAt이 필요합니다");
		}
		if ((status == PostRecipientStatus.ANSWERED || status == PostRecipientStatus.SKIPPED || status == PostRecipientStatus.EXPIRED || status == PostRecipientStatus.BLOCKED) != (capacityReleasedAt != null)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE,
				"capacityReleasedAt",
				"terminal 상태와 capacityReleasedAt이 일치해야 합니다"
			);
		}
	}

	public static PostRecipient available(Long postId, Long recipientId, String distanceBand,
		BigDecimal matchedBearingDegrees, String matchedRegionCode, Instant matchedAt,
		BigDecimal inboundBearingDegrees, long distanceM) {
		return new PostRecipient(null, postId, recipientId, PostRecipientStatus.AVAILABLE,
			distanceBand, matchedBearingDegrees, matchedRegionCode, matchedAt, null, null, null, null, null, null, null,
			inboundBearingDegrees, distanceM, null);
	}

	public static PostRecipient restore(Long id, Long postId, Long recipientId, PostRecipientStatus status,
		String distanceBand, BigDecimal matchedBearingDegrees, String matchedRegionCode, Instant matchedAt,
		Instant discoveredAt, Instant openedAt, Instant skipRequestedAt, Instant skippedAt,
		Instant capacityReleasedAt, Instant expiredAt, Instant blockedAt,
		BigDecimal inboundBearingDegrees, long distanceM, Instant answersReadAt) {
		return new PostRecipient(id, postId, recipientId, status, distanceBand, matchedBearingDegrees,
			matchedRegionCode, matchedAt, discoveredAt, openedAt, skipRequestedAt, skippedAt,
			capacityReleasedAt, expiredAt, blockedAt, inboundBearingDegrees, distanceM, answersReadAt);
	}

	private void validateTimestamp(Instant value, String field) {
		if (value != null && value.isBefore(matchedAt)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_TIME_ORDER, field, field + "은 matchedAt보다 빠를 수 없습니다");
		}
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new DirectionException(
				DirectionErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}

	private static Long requireId(Long value, String field) {
		if (value == null || value <= 0) {
			throw new DirectionException(DirectionErrorCode.INVALID_ID, field, field + "는 양수여야 합니다");
		}
		return value;
	}

	private static String requireText(String value, String field, int max) {
		if (value == null || value.isBlank() || value.length() > max) {
			throw new DirectionException(DirectionErrorCode.INVALID_TEXT, field, field + "이 유효하지 않습니다");
		}
		return value;
	}

	public PostRecipient requestSkip(Instant at) {
		requireValue(at, "at");
		if (!isOpenForTransition()) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE, "status", "넘김을 요청할 수 없는 상태입니다");
		}
		return new PostRecipient(id, postId, recipientId, PostRecipientStatus.SKIP_PENDING, distanceBand,
			matchedBearingDegrees, matchedRegionCode, matchedAt, discoveredAt, openedAt, at, null,
			null, null, null, inboundBearingDegrees, distanceM, answersReadAt);
	}

	public PostRecipient revertSkip() {
		if (status != PostRecipientStatus.SKIP_PENDING) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE, "status", "되돌릴 수 있는 상태가 아닙니다");
		}
		return new PostRecipient(id, postId, recipientId, previousStatus(), distanceBand,
			matchedBearingDegrees, matchedRegionCode, matchedAt, discoveredAt, openedAt, null, null,
			null, null, null, inboundBearingDegrees, distanceM, answersReadAt);
	}

	public PostRecipient confirmSkip(Instant at) {
		requireValue(at, "at");
		if (status != PostRecipientStatus.SKIP_PENDING) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE, "status", "확정할 수 있는 상태가 아닙니다");
		}
		// capacityReleasedAt은 이 객체 상태에만 반영된다. RecipientReceiveStateRepository의 실제 용량
		// 카운터 해제는 후속 SKIP_CONFIRMATION_DUE 워커의 몫이며 이 메서드 범위 밖이다.
		return new PostRecipient(id, postId, recipientId, PostRecipientStatus.SKIPPED, distanceBand,
			matchedBearingDegrees, matchedRegionCode, matchedAt, discoveredAt, openedAt, skipRequestedAt, at,
			at, null, null, inboundBearingDegrees, distanceM, answersReadAt);
	}

	private PostRecipientStatus previousStatus() {
		if (openedAt != null) return PostRecipientStatus.OPENED;
		if (discoveredAt != null) return PostRecipientStatus.DISCOVERED;
		return PostRecipientStatus.AVAILABLE;
	}

	/** AVAILABLE만 DISCOVERED로 올린다. 이미 열람 이력이 있으면 그대로 둔다. */
	public PostRecipient discover(Instant at) {
		requireValue(at, "at");
		if (status == PostRecipientStatus.DISCOVERED || status == PostRecipientStatus.OPENED) return this;
		if (status != PostRecipientStatus.AVAILABLE) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE, "status", "발견 처리를 할 수 없는 상태입니다");
		}
		return new PostRecipient(id, postId, recipientId, PostRecipientStatus.DISCOVERED, distanceBand,
			matchedBearingDegrees, matchedRegionCode, matchedAt, at, null, null, null, null, null, null,
			inboundBearingDegrees, distanceM, answersReadAt);
	}

	/**
	 * 멱등이며 최초 열람 시각을 유지한다.
	 * AVAILABLE에서 바로 열면 discoveredAt도 함께 채운다 — 생성자 검증과
	 * ck_post_recipient_status_timestamps가 OPENED에 두 시각을 모두 요구한다.
	 */
	public PostRecipient open(Instant at) {
		requireValue(at, "at");
		if (status == PostRecipientStatus.OPENED) return this;
		if (status != PostRecipientStatus.AVAILABLE && status != PostRecipientStatus.DISCOVERED) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE, "status", "열람 처리를 할 수 없는 상태입니다");
		}
		if (discoveredAt != null && at.isBefore(discoveredAt)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_TIME_ORDER, "at", "at은 discoveredAt보다 빠를 수 없습니다");
		}
		return new PostRecipient(id, postId, recipientId, PostRecipientStatus.OPENED, distanceBand,
			matchedBearingDegrees, matchedRegionCode, matchedAt, discoveredAt == null ? at : discoveredAt, at,
			null, null, null, null, null, inboundBearingDegrees, distanceM, answersReadAt);
	}

	/**
	 * 답변 확정. capacityReleasedAt을 함께 설정한다 —
	 * ct_post_recipient_capacity_release가 ANSWERED와 capacity_released_at의 동치성을
	 * commit 시점에 검사한다. 실제 카운터 감소는 호출자가 RecipientReceiveStateRepository로 수행한다.
	 */
	public PostRecipient answered(Instant at) {
		requireValue(at, "at");
		if (status == PostRecipientStatus.ANSWERED) return this;
		if (!isOpenForTransition()) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE, "status", "답변 처리를 할 수 없는 상태입니다");
		}
		requireAtNotBeforeDiscoveryOrOpen(at);
		return new PostRecipient(id, postId, recipientId, PostRecipientStatus.ANSWERED, distanceBand,
			matchedBearingDegrees, matchedRegionCode, matchedAt, discoveredAt == null ? at : discoveredAt,
			openedAt == null ? at : openedAt, null, null, at, null, null,
			inboundBearingDegrees, distanceM, answersReadAt);
	}

	/**
	 * 질문글이 만료됐는데 이 수신자가 응답하지 않은 경우의 전이. `SKIP_PENDING`은
	 * 유효 소스 상태가 아니다 — 되돌리기 유예 동안은 수신 용량을 계속 붙잡는다는
	 * 기존 설계(`confirmSkip`/`revertSkip` 전용 레인)를 그대로 지킨다. 만료 sweep의
	 * 대상 조회가 `SKIP_PENDING`을 애초에 후보에서 제외하므로, 이 메서드가 그 상태를
	 * 받으면 항상 호출자 버그다.
	 * `confirmSkip()`과 같은 일회성 전이 계약을 따른다 — 이미 `EXPIRED`이거나 다른
	 * terminal 상태인 행에 재호출하면 예외를 던진다. 재실행에서 카운터를 중복
	 * 감소시키지 않을 책임은 호출자(대상 조회 시점에 이미 terminal인 행을 제외)에
	 * 있다.
	 */
	public PostRecipient expire(Instant at) {
		requireValue(at, "at");
		if (!isOpenForTransition()) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE, "status", "만료 처리를 할 수 없는 상태입니다");
		}
		requireAtNotBeforeDiscoveryOrOpen(at);
		return new PostRecipient(id, postId, recipientId, PostRecipientStatus.EXPIRED, distanceBand,
			matchedBearingDegrees, matchedRegionCode, matchedAt, discoveredAt, openedAt, null, null,
			at, at, null, inboundBearingDegrees, distanceM, answersReadAt);
	}

	/**
	 * 차단 성립에 따른 전이. 차단한 사람 자신의 수신 항목에만 적용한다 — 호출자가
	 * 차단대상이 아니라 차단자 관점에서 대상을 고른다(feed 조회의
	 * `ub.blocker_id = 뷰어` 필터 방향과 동일). `SKIP_PENDING`도 유효 소스다 —
	 * 넘김 여부를 더 이상 판정할 이유가 없어졌기 때문이다. 전이하면서
	 * `skipRequestedAt`을 비운다 — `SKIP_PENDING`이 아닌 상태로 그 값이 남아 있으면
	 * 생성자 불변식(`(status == SKIP_PENDING) == (skipRequestedAt != null && skippedAt == null)`)을
	 * 위반한다.
	 */
	public PostRecipient block(Instant at) {
		requireValue(at, "at");
		if (!isBlockable()) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE, "status", "차단 처리를 할 수 없는 상태입니다");
		}
		return new PostRecipient(id, postId, recipientId, PostRecipientStatus.BLOCKED, distanceBand,
			matchedBearingDegrees, matchedRegionCode, matchedAt, discoveredAt, openedAt, null, null,
			at, null, at, inboundBearingDegrees, distanceM, answersReadAt);
	}

	/**
	 * 이 수신자가 그 질문글의 답변 목록을 읽은 시각을 기록한다. `새로운 답변 n개` 배지는
	 * 이 시각 이후 공개된 답변만 센다. direction_post.answers_read_at(질문자 전용)과
	 * 별도로 수신자마다 기준선이 필요하다 — 답변이 전원에게 공개되면서 사람마다 마지막으로
	 * 본 시점이 달라졌기 때문이다.
	 */
	public PostRecipient markAnswersRead(Instant at) {
		requireValue(at, "at");
		return new PostRecipient(id, postId, recipientId, status, distanceBand,
			matchedBearingDegrees, matchedRegionCode, matchedAt, discoveredAt, openedAt, skipRequestedAt, skippedAt,
			capacityReleasedAt, expiredAt, blockedAt, inboundBearingDegrees, distanceM, at);
	}

	/**
	 * 아직 답변·넘김확정·만료·차단 중 어느 쪽으로도 종결되지 않은 상태다.
	 * requestSkip/answered/expire의 소스 상태 조건이자, `answer`·`direction` 양쪽에서
	 * "이 항목이 여전히 열려 있는가"를 판단하는 단일 기준이다 — 호출자마다 상태 목록을
	 * 다시 나열하지 않고 이 메서드를 참조한다.
	 */
	public boolean isOpenForTransition() {
		return status == PostRecipientStatus.AVAILABLE || status == PostRecipientStatus.DISCOVERED
			|| status == PostRecipientStatus.OPENED;
	}

	/** block()의 소스 상태 조건. SKIP_PENDING도 차단 대상이 될 수 있다는 점만 isOpenForTransition과 다르다. */
	public boolean isBlockable() {
		return isOpenForTransition() || status == PostRecipientStatus.SKIP_PENDING;
	}

	/** answered()와 expire()가 공유하는 시간 역전 방어. 둘 다 discoveredAt·openedAt 이후에만 전이할 수 있다. */
	private void requireAtNotBeforeDiscoveryOrOpen(Instant at) {
		if (discoveredAt != null && at.isBefore(discoveredAt)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_TIME_ORDER, "at", "at은 discoveredAt보다 빠를 수 없습니다");
		}
		if (openedAt != null && at.isBefore(openedAt)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_TIME_ORDER, "at", "at은 openedAt보다 빠를 수 없습니다");
		}
	}

}
