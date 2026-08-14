/**
 * Created at: 2026-08-14T13:00:00+09:00
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-003
 */
package com.dnd.qello.direction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.direction.config.DirectionPostProperties;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

class DirectionPostPolicyTest {

	private static final Instant SUBMITTED_AT = Instant.parse("2026-08-14T04:00:00Z");
	private final DirectionPostPolicy policy = new DirectionPostPolicy(new DirectionPostProperties(
		DirectionPostProperties.DeliveryScope.GLOBAL, 0, 20_100_000L, Duration.ofHours(12), 300, 1));

	@Test
	@DisplayName("본문은 NFC와 바깥 공백을 정규화하고 Unicode code point로 제한한다")
	void normalizesBodyAndCountsCodePoints() {
		assertThat(policy.normalizeBody("  e\u0301  ")).isEqualTo("é");
		assertThat(policy.normalizeBody("😀".repeat(300))).hasSize(600);
		assertThatThrownBy(() -> policy.normalizeBody("😀".repeat(301)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_TEXT);
	}

	@Test
	@DisplayName("텍스트만 이미지 한 장만 텍스트와 이미지 조합을 허용한다")
	void acceptsSupportedContentCombinations() {
		assertThat(policy.validateContent("본문", List.of()).bodyText()).isEqualTo("본문");
		assertThat(policy.validateContent(null, List.of(101L)).mediaIds()).containsExactly(101L);
		assertThat(policy.validateContent("본문", List.of(101L)).bodyText()).isEqualTo("본문");
	}

	@Test
	@DisplayName("빈 콘텐츠와 두 장 또는 중복 media는 direction 오류로 거절한다")
	void rejectsInvalidContentCombinations() {
		assertThatThrownBy(() -> policy.validateContent("   ", List.of()))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.REQUIRED_VALUE_MISSING);
		assertThatThrownBy(() -> policy.validateContent(null, List.of(101L, 102L)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_VALUE_RANGE);
		assertThatThrownBy(() -> policy.validateContent(null, List.of(101L, 101L)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_VALUE_RANGE);
	}

	@Test
	@DisplayName("만료 시각은 서버 제출 시각에 PT12H를 더해 계산한다")
	void calculatesExpirationFromServerSubmissionTime() {
		assertThat(policy.expiresAt(SUBMITTED_AT)).isEqualTo(Instant.parse("2026-08-14T16:00:00Z"));
	}
}
