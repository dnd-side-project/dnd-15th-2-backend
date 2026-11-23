/**
 * Created at: 2026-11-23T16:43:12+09:00
 * Source scenario: TEST-PLAN-GH-379-LOCATION-GATE-UNIT-002, TEST-PLAN-GH-379-LOCATION-GATE-UNIT-003,
 * TEST-PLAN-GH-379-LOCATION-GATE-UNIT-004
 */
package com.dnd.qello.direction.gate;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocationCollectionGateTest {

	private static final String SECRET_LIKE_REFERENCE = "short";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(LocationCollectionGate.class);

	@Test
	@DisplayName("게이트가 꺼져 있으면 신고 수리 참조와 프로필을 검사하지 않고 기동한다")
	void startsWithoutCheckingReferenceWhenDisabled() {
		runner.withPropertyValues("qello.location.collection.enabled=false")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getBean(LocationCollectionGate.class).isEnabled()).isFalse();
				});
	}

	@Test
	@DisplayName("게이트 속성이 없으면 기본값 꺼짐으로 기동한다")
	void defaultsToDisabled() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(LocationCollectionGate.class).isEnabled()).isFalse();
		});
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "default", "test", "prod", "test,prod"})
	@DisplayName("dev·local이 아닌 프로필에서 신고 수리 참조 없이 게이트를 켜면 기동이 실패하고 참조 값은 메시지에 남지 않는다")
	void failsToStartWithoutReferenceOutsideNonProductionProfiles(String profiles) {
		runner.withPropertyValues(properties(profiles, SECRET_LIKE_REFERENCE))
				.run(context -> {
					assertThat(context).hasFailed();
					Throwable root = rootCause(context.getStartupFailure());
					assertThat(root).isInstanceOf(IllegalStateException.class)
							.hasMessageContaining("qello.location.collection.lbs-registration-reference")
							.hasMessageNotContaining(SECRET_LIKE_REFERENCE);
				});
	}

	@ParameterizedTest
	@ValueSource(strings = {"dev", "local", "dev,prod", "local,test"})
	@DisplayName("활성 프로필에 dev 또는 local이 있으면 신고 수리 참조 없이도 게이트를 켠 채 기동한다")
	void startsWithoutReferenceInNonProductionProfiles(String profiles) {
		runner.withPropertyValues(properties(profiles, null))
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getBean(LocationCollectionGate.class).isEnabled()).isTrue();
				});
	}

	@Test
	@DisplayName("dev·local이 아닌 프로필도 추적 가능한 신고 수리 참조가 있으면 게이트를 켠 채 기동한다")
	void startsWithTraceableReferenceOutsideNonProductionProfiles() {
		runner.withPropertyValues(properties("test", "LBS-2026-000123"))
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getBean(LocationCollectionGate.class).isEnabled()).isTrue();
				});
	}

	@Test
	@DisplayName("게이트가 꺼져 있으면 ensureEnabled가 503 위치 기능 비활성 오류를 던진다")
	void ensureEnabledRejectsWhenDisabled() {
		LocationCollectionGate gate = new LocationCollectionGate(new LocationCollectionGateProperties(false, null),
				new StandardEnvironment());

		assertThatThrownBy(gate::ensureEnabled)
				.isInstanceOf(DirectionException.class)
				.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.LOCATION_COLLECTION_DISABLED);
	}

	@Test
	@DisplayName("게이트가 켜져 있으면 ensureEnabled가 통과한다")
	void ensureEnabledPassesWhenEnabled() {
		LocationCollectionGate gate = new LocationCollectionGate(
				new LocationCollectionGateProperties(true, "LBS-2026-000123"), new StandardEnvironment());

		assertThatCode(gate::ensureEnabled).doesNotThrowAnyException();
	}

	private static String[] properties(String profiles, String reference) {
		List<String> values = new ArrayList<>();
		values.add("qello.location.collection.enabled=true");
		if (!profiles.isEmpty()) {
			values.add("spring.profiles.active=" + profiles);
		}
		if (reference != null) {
			values.add("qello.location.collection.lbs-registration-reference=" + reference);
		}
		return values.toArray(String[]::new);
	}

	private static Throwable rootCause(Throwable failure) {
		Throwable current = failure;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current;
	}
}
