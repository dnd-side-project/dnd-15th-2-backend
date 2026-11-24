/**
 * Created at: 2026-11-23T16:43:12+09:00
 * Source scenario: TEST-PLAN-GH-379-LOCATION-GATE-UNIT-007, TEST-PLAN-GH-379-LOCATION-GATE-UNIT-008
 */
package com.dnd.qello.direction.gate;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;

import com.dnd.qello.common.error.ErrorCategory;
import com.dnd.qello.direction.error.DirectionErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

class LocationCollectionGateConfigurationFilesTest {

	private static final String ENABLED = "qello.location.collection.enabled";
	private static final String REFERENCE = "qello.location.collection.lbs-registration-reference";

	@Test
	@DisplayName("기본 application.yml은 위치 수집 게이트를 끄고 신고 수리 참조를 두지 않는다")
	void defaultConfigurationKeepsGateClosed() throws IOException {
		PropertySource<?> source = load("application.yml");

		assertThat(String.valueOf(source.getProperty(ENABLED))).isEqualTo("false");
		assertThat(source.getProperty(REFERENCE)).isNull();
	}

	@Test
	@DisplayName("dev·local 프로필 설정은 위치 수집 게이트를 켜고 신고 수리 참조 값을 쓰지 않는다")
	void nonProductionProfilesOpenGateWithoutReference() throws IOException {
		for (String file : List.of("application-dev.yml", "application-local.yml")) {
			PropertySource<?> source = load(file);

			assertThat(String.valueOf(source.getProperty(ENABLED))).as(file).isEqualTo("true");
			assertThat(source.getProperty(REFERENCE)).as(file).isNull();
		}
	}

	@Test
	@DisplayName("위치 기능 비활성 오류는 503, DIR-APP-008, APP 분류이고 다른 방향 오류 코드와 겹치지 않는다")
	void locationCollectionDisabledErrorCodeContract() {
		DirectionErrorCode code = DirectionErrorCode.LOCATION_COLLECTION_DISABLED;

		assertThat(code.httpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(code.code()).isEqualTo("DIR-APP-008");
		assertThat(code.category()).isEqualTo(ErrorCategory.APP);
		assertThat(Arrays.stream(DirectionErrorCode.values()).map(DirectionErrorCode::code))
				.doesNotHaveDuplicates();
	}

	private static PropertySource<?> load(String file) throws IOException {
		List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
		assertThat(sources).as(file).hasSize(1);
		return sources.get(0);
	}
}
