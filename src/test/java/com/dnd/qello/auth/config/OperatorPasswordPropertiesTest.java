/*
 * Created at: 2026-11-24T23:50:07+09:00
 * Source scenario: TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-008
 */
package com.dnd.qello.auth.config;

import java.time.Duration;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OperatorPasswordPropertiesTest {

	@Test
	@DisplayName("UNIT-008: max-age가 없으면 바인딩에 실패한다")
	void rejectsMissingMaxAge() {
		assertThatNullPointerException()
				.isThrownBy(() -> new OperatorPasswordProperties(null))
				.withMessageContaining("qello.auth.operator-password.max-age");
	}

	@ParameterizedTest(name = "max-age = {0}")
	@DisplayName("UNIT-008: max-age가 0 이하면 바인딩에 실패한다")
	@ValueSource(strings = {"PT0S", "-P1D"})
	void rejectsNonPositiveMaxAge(String value) {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new OperatorPasswordProperties(Duration.parse(value)))
				.withMessageContaining("qello.auth.operator-password.max-age");
	}

	@Test
	@DisplayName("UNIT-008: application.yml의 기본 변경 주기는 90일이다")
	void defaultMaxAgeIsNinetyDays() {
		YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
		yaml.setResources(new ClassPathResource("application.yml"));
		Properties properties = yaml.getObject();

		String maxAge = properties.getProperty("qello.auth.operator-password.max-age");

		assertThat(maxAge).isEqualTo("P90D");
		assertThat(new OperatorPasswordProperties(Duration.parse(maxAge)).maxAge()).isEqualTo(Duration.ofDays(90));
	}
}
