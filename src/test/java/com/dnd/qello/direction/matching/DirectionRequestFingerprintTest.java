package com.dnd.qello.direction.matching;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.direction.domain.DirectionRequestFingerprint;

/**
 * Created at: 2026-08-11T20:05:00+09:00
 * Source scenario: TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-UNIT-001 through UNIT-002
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
			.isEqualTo("v1:9c1664fb8d1dfb68df9454bfc05cd1523ea5d8362d491f7a091f9949e07b4c0d");
	}

	@Test
	@DisplayName("fingerprint 대상 필드가 의미 있게 달라지면 fingerprint도 달라진다")
	void changesWhenIntentChanges() {
		DirectionRequestFingerprint base = fingerprint("SEGMENT-A", "산책 중");

		assertThat(fingerprint("SEGMENT-A", "산책  중")).isNotEqualTo(base);
		assertThat(fingerprint("segment-a", "산책 중")).isNotEqualTo(base);
		assertThat(DirectionRequestFingerprint.create(42L, 7L, "SEGMENT-A", 101, 5000,
			"KR-SEOUL", "산책 중")).isNotEqualTo(base);
	}

	@Test
	@DisplayName("bodyText null은 빈 문자열과 구분되는 canonical 입력으로 보존된다")
	void distinguishesNullBodyFromEmptyMeaning() {
		DirectionRequestFingerprint withoutBody = DirectionRequestFingerprint.create(42L, 7L,
			"SEGMENT-A", 100, 5000, "KR-SEOUL", null);
		DirectionRequestFingerprint withBody = fingerprint("SEGMENT-A", "본문");

		assertThat(withoutBody).isNotEqualTo(withBody);
		assertThat(DirectionRequestFingerprint.restore(withoutBody.value())).isEqualTo(withoutBody);
		assertThat(DirectionRequestFingerprint.restore(null)).isNull();
	}

	private DirectionRequestFingerprint fingerprint(String segmentKey, String bodyText) {
		return DirectionRequestFingerprint.create(42L, 7L, segmentKey, 100, 5000, "KR-SEOUL", bodyText);
	}
}
