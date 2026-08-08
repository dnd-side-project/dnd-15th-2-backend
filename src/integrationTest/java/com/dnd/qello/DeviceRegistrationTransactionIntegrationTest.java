/**
 * Created at: 2026-08-08T18:24:00+09:00
 * Source scenario: TEST-PLAN-GH-88-COUNTRY-ONBOARDING-UNIT-005
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.auth.domain.DeviceCredential;
import com.dnd.qello.auth.domain.DevicePlatform;
import com.dnd.qello.auth.repository.DeviceCredentialRepository;
import com.dnd.qello.auth.service.DeviceRegistrationService;

@SpringBootTest
@ActiveProfiles({"test", "account-persistence"})
@Import(DeviceRegistrationTransactionIntegrationTest.TestDoubleConfiguration.class)
class DeviceRegistrationTransactionIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION_CODE = "TEST-TRANSACTION-COUNTRY";

	@Autowired
	private DeviceRegistrationService registrationService;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private DeviceCredentialRepository credentialRepository;

	@BeforeEach
	void resetFixtures() {
		jdbcTemplate.update("DELETE FROM user_account");
		jdbcTemplate.update("DELETE FROM region_code WHERE code = ?", REGION_CODE);
		jdbcTemplate.update("DELETE FROM region_code WHERE code = 'KR'");
		jdbcTemplate.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY'), (?, 'KR', 'Transaction Test', 'REGION')
			""", REGION_CODE);
	}

	@Test
	@DisplayName("자격증명 저장 실패는 계정 저장까지 롤백한다")
	void rollsBackAccountWhenCredentialPersistenceFails() {
		when(credentialRepository.findActiveByInstallationId(anyString())).thenReturn(Optional.empty());
		when(credentialRepository.save(any(DeviceCredential.class)))
			.thenThrow(new IllegalStateException("credential persistence failure"));

		assertThatThrownBy(() -> registrationService.register(
			"transaction-installation",
			DevicePlatform.IOS,
			"KR",
			REGION_CODE,
			"ko-KR",
			"Asia/Seoul",
			"바람"))
			.isInstanceOf(IllegalStateException.class);

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM user_account", Integer.class)).isZero();
		assertThat(accountRepository.findById(1L)).isEmpty();
	}

	@TestConfiguration
	static class TestDoubleConfiguration {

		@Bean
		@Primary
		DeviceCredentialRepository deviceCredentialRepository() {
			return mock(DeviceCredentialRepository.class);
		}
	}

}
