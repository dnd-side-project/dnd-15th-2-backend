package com.dnd.qello.direction.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import com.dnd.qello.direction.domain.DirectionRequestFingerprint;
import com.dnd.qello.direction.error.DirectionException;

/**
 * Created at: 2026-08-14T12:44:00+09:00
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-008 through UNIT-010
 */
class DirectionRequestFingerprintTest {

	@Test
	@DisplayName("NFC와 바깥 Unicode 공백을 정규화한 fingerprint는 deterministic하다")
	void normalizesNfcAndOuterUnicodeWhitespace() {
		DirectionRequestFingerprint normalized = fingerprint("\u00a0SEGMENT-A\u00a0", "\u00a0Cafe\u0301\u00a0");
		DirectionRequestFingerprint equivalent = fingerprint("SEGMENT-A", "Caf\u00e9");

		assertThat(normalized.value()).isEqualTo(equivalent.value())
			.startsWith("v1:").hasSize(67);
		assertThat(fingerprint("SEGMENT-A", "산책 중").value())
			.isEqualTo("v1:005ef30eefec6e46a94a96e11250092b26c627874ab6732dd235426d302c6671");
	}

	@Test
	@DisplayName("fingerprint 대상 필드가 의미 있게 달라지면 fingerprint도 달라진다")
	void changesWhenIntentChanges() {
		DirectionRequestFingerprint base = fingerprint("SEGMENT-A", "산책 중");

		assertThat(fingerprint("SEGMENT-A", "산책  중")).isNotEqualTo(base);
		assertThat(fingerprint("segment-a", "산책 중")).isNotEqualTo(base);
		assertThat(DirectionRequestFingerprint.create(42L, 7L, "SEGMENT-A", 101, 5000,
			"산책 중")).isNotEqualTo(base);
	}

	@Test
	@DisplayName("bodyText null은 canonical 입력으로 보존되고 빈 문자열과 구분된다")
	void distinguishesNullBodyFromEmptyMeaning() {
		DirectionRequestFingerprint withoutBody = DirectionRequestFingerprint.create(42L, 7L,
			"SEGMENT-A", 100, 5000, null);
		DirectionRequestFingerprint withBody = fingerprint("SEGMENT-A", "본문");

		assertThat(withoutBody).isNotEqualTo(withBody);
		assertThat(DirectionRequestFingerprint.restore(withoutBody.value())).isEqualTo(withoutBody);
		assertThat(DirectionRequestFingerprint.restore(null)).isNull();
		assertThatThrownBy(() -> DirectionRequestFingerprint.restore(""))
			.isInstanceOf(DirectionException.class);
		assertThatThrownBy(() -> fingerprint("   ", "본문"))
			.isInstanceOf(DirectionException.class);
	}

	@Test
	@DisplayName("v1 fingerprint는 미디어 ID를 포함하고 서버 snapshot은 포함하지 않는다")
	void v1IncludesMediaAndExcludesServerSnapshot() {
		DirectionRequestFingerprint sameIntent = DirectionRequestFingerprint.create(
			42L, 7L, "SEGMENT-A", 100, 5000, "본문", List.of(99L));
		DirectionRequestFingerprint sameIntentRetry = DirectionRequestFingerprint.create(
			42L, 7L, "SEGMENT-A", 100, 5000, "본문", List.of(99L));
		DirectionRequestFingerprint differentMedia = DirectionRequestFingerprint.create(
			42L, 7L, "SEGMENT-A", 100, 5000, "본문", List.of(100L));

		assertThat(sameIntent).isEqualTo(sameIntentRetry).isNotEqualTo(differentMedia);
		assertThat(sameIntent.value()).startsWith("v1:").hasSize(67);
		assertThat(DirectionRequestFingerprint.restore(sameIntent.value())).isEqualTo(sameIntent);
	}

	private DirectionRequestFingerprint fingerprint(String segmentKey, String bodyText) {
		return DirectionRequestFingerprint.create(42L, 7L, segmentKey, 100, 5000, bodyText);
	}
}
