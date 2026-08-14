package com.dnd.qello.direction.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

/**
 * 질문글 발송 정책의 단일 설정 경계.
 *
 * <p>거리·만료·본문·미디어 상한은 preview와 submit/worker가 같은 snapshot을
 * 사용해야 하므로 web 요청이나 SQL에 기본값을 중복해서 두지 않는다.</p>
 */
@ConfigurationProperties(prefix = "qello.direction.post")
public record DirectionPostProperties(
	DeliveryScope deliveryScope,
	long minDistanceMeters,
	long maxDistanceMeters,
	Duration ttl,
	int maxBodyCodePoints,
	int maxMediaCount
) {

	public static final long GLOBAL_MAX_DISTANCE_METERS = 20_100_000L;
	public static final int APPROVED_MAX_BODY_CODE_POINTS = 300;
	public static final int APPROVED_MAX_MEDIA_COUNT = 1;

	public DirectionPostProperties {
		if (deliveryScope == null) {
			throw invalid("deliveryScope", "발송 범위는 필수입니다");
		}
		if (minDistanceMeters < 0) {
			throw invalid("minDistanceMeters", "최소 거리는 음수일 수 없습니다");
		}
		if (maxDistanceMeters <= minDistanceMeters || maxDistanceMeters > GLOBAL_MAX_DISTANCE_METERS) {
			throw new DirectionException(DirectionErrorCode.INVALID_DISTANCE_RANGE, "maxDistanceMeters",
				"최대 거리가 허용 범위를 벗어났습니다");
		}
		if (ttl == null || ttl.isZero() || ttl.isNegative()) {
			throw invalid("ttl", "질문글 TTL은 양수여야 합니다");
		}
		if (maxBodyCodePoints != APPROVED_MAX_BODY_CODE_POINTS) {
			throw invalid("maxBodyCodePoints", "질문글 본문 상한은 300 code point여야 합니다");
		}
		if (maxMediaCount != APPROVED_MAX_MEDIA_COUNT) {
			throw invalid("maxMediaCount", "질문글 미디어 상한은 1장이어야 합니다");
		}
	}

	public boolean isGlobal() {
		return deliveryScope == DeliveryScope.GLOBAL;
	}

	private static DirectionException invalid(String field, String reason) {
		return new DirectionException(DirectionErrorCode.INVALID_VALUE_RANGE, field, reason);
	}

	/** 현재 승인된 발송 범위. 이후 범위를 추가할 때는 별도 정책 승인이 필요하다. */
	public enum DeliveryScope {
		GLOBAL
	}
}
