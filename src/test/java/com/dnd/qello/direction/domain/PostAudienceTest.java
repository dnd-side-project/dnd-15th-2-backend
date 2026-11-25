/**
 * Created at: 2026-11-24T17:42:01Z
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-UNIT-013
 */
package com.dnd.qello.direction.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.direction.error.DirectionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostAudienceTest {

	@Test
	@DisplayName("폐기된 원점 없는 과거 audience는 메타데이터를 복원한다")
	void restoresDiscardedOriginWithoutInventingCoordinates() {
		Instant snapshot = Instant.parse("2026-11-24T00:00:00Z");
		PostAudience restored = PostAudience.restore(1L, 2L, "N", BigDecimal.ZERO,
				BigDecimal.valueOf(45), 0, 10000, null, null, null, snapshot);

		assertThat(restored.getOriginLatitude()).isNull();
		assertThat(restored.getOriginLongitude()).isNull();
		assertThat(restored.getOriginCellId()).isNull();
		assertThat(restored.getMinDistanceMeters()).isZero();
		assertThat(restored.getMaxDistanceMeters()).isEqualTo(10000);
		assertThat(restored.getSelectedSegmentKey()).isEqualTo("N");
		assertThat(restored.getSnapshottedAt()).isEqualTo(snapshot);
	}

	@Test
	@DisplayName("신규 audience 생성에는 여전히 원점 또는 셀이 필요하다")
	void rejectsNewAudienceWithoutLocation() {
		assertThatThrownBy(() -> PostAudience.create(1L, 2L, "N", BigDecimal.ZERO,
				BigDecimal.valueOf(45), 0, 10000, null, null, null, Instant.parse("2026-11-24T00:00:00Z")))
				.isInstanceOf(DirectionException.class);
	}

	@Test
	@DisplayName("복원에서도 위도와 경도 편측 누락은 거부한다")
	void rejectsPartialCoordinatesOnRestore() {
		assertThatThrownBy(() -> PostAudience.restore(1L, 2L, "N", BigDecimal.ZERO,
				BigDecimal.valueOf(45), 0, 10000, BigDecimal.ONE, null, null,
				Instant.parse("2026-11-24T00:00:00Z")))
				.isInstanceOf(DirectionException.class);
	}
}
