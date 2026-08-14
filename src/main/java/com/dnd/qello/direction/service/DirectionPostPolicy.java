package com.dnd.qello.direction.service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.dnd.qello.direction.config.DirectionPostProperties;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

/**
 * 질문글 정책의 순수 application 경계.
 *
 * <p>미디어 소유권·READY 상태는 MediaAttachmentService가 검증하고, 이 객체는
 * 요청 형태와 정책 숫자만 검증한다. 따라서 web, submit service, 테스트가 같은
 * 정규화·길이·조합 규칙을 공유할 수 있다.</p>
 */
@Component
public final class DirectionPostPolicy {

	private final DirectionPostProperties properties;

	public DirectionPostPolicy(DirectionPostProperties properties) {
		this.properties = properties;
	}

	public DirectionPostProperties properties() {
		return properties;
	}

	public long minDistanceMeters() {
		return properties.minDistanceMeters();
	}

	public long maxDistanceMeters() {
		return properties.maxDistanceMeters();
	}

	public Duration ttl() {
		return properties.ttl();
	}

	public boolean isGlobal() {
		return properties.isGlobal();
	}

	/** 서버 제출 시각을 기준으로 최초 만료 시각을 계산한다. */
	public Instant expiresAt(Instant submittedAt) {
		if (submittedAt == null) {
			throw new DirectionException(DirectionErrorCode.REQUIRED_VALUE_MISSING, "submittedAt",
				"submittedAt은 필수입니다");
		}
		return submittedAt.plus(properties.ttl());
	}

	/**
	 * NFC 정규화와 바깥 공백 제거를 적용한다. 공백만 있는 본문은 미디어 전용
	 * 게시글을 허용할 수 있도록 null로 취급한다.
	 */
	public String normalizeBody(String bodyText) {
		if (bodyText == null) {
			return null;
		}
		String normalized = Normalizer.normalize(bodyText, Normalizer.Form.NFC).strip();
		if (normalized.isEmpty()) {
			return null;
		}
		int codePoints = normalized.codePointCount(0, normalized.length());
		if (codePoints > properties.maxBodyCodePoints()) {
			throw new DirectionException(DirectionErrorCode.INVALID_TEXT, "bodyText",
				"질문글 본문이 허용된 code point 수를 초과했습니다");
		}
		return normalized;
	}

	/**
	 * 본문과 media ID의 허용 조합을 검증하고, 저장에 사용할 정규화 값을 반환한다.
	 * media ID의 소유권·상태·기존 첨부 여부는 저장소 경계에서 추가로 검사한다.
	 */
	public ValidatedContent validateContent(String bodyText, Collection<Long> mediaIds) {
		String normalizedBody = normalizeBody(bodyText);
		List<Long> normalizedMediaIds = mediaIds == null ? List.of() : new ArrayList<>(mediaIds);
		if (normalizedMediaIds.size() > properties.maxMediaCount()) {
			throw new DirectionException(DirectionErrorCode.INVALID_VALUE_RANGE, "mediaIds",
				"질문글 미디어는 최대 1장까지 첨부할 수 있습니다");
		}
		Set<Long> uniqueMediaIds = new HashSet<>();
		for (Long mediaId : normalizedMediaIds) {
			if (mediaId == null || mediaId <= 0) {
				throw new DirectionException(DirectionErrorCode.INVALID_ID, "mediaIds",
					"mediaId는 양수여야 합니다");
			}
			if (!uniqueMediaIds.add(mediaId)) {
				throw new DirectionException(DirectionErrorCode.INVALID_VALUE_RANGE, "mediaIds",
					"같은 media를 중복 첨부할 수 없습니다");
			}
		}
		if (normalizedBody == null && normalizedMediaIds.isEmpty()) {
			throw new DirectionException(DirectionErrorCode.REQUIRED_VALUE_MISSING, "content",
				"본문 또는 media가 하나 이상 필요합니다");
		}
		return new ValidatedContent(normalizedBody, normalizedMediaIds);
	}

	public record ValidatedContent(String bodyText, List<Long> mediaIds) {
		public ValidatedContent {
			mediaIds = List.copyOf(mediaIds);
		}
	}
}
