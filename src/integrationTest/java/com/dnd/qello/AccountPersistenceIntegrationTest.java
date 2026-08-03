/**
 * Created at: 2026-08-03T18:13:05+09:00
 * Source scenario: TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-INT-001
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;

/**
 * Created at: 2026-08-03T18:13:05+09:00
 * Source scenario: TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-INT-001 through INT-005
 */
@SpringBootTest
@ActiveProfiles({"test", "account-persistence"})
@Import(AccountPersistenceIntegrationTest.TestClockConfiguration.class)
class AccountPersistenceIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION_CODE = "TEST-COUNTRY";
	private static final Instant FIRST_AUDIT_TIME =
		Instant.parse("2026-08-03T09:00:00Z");

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void resetDatabaseAndClock() {
		clock.setInstant(FIRST_AUDIT_TIME);
		jdbcTemplate.update("DELETE FROM user_account");
		jdbcTemplate.update("DELETE FROM region_code WHERE code = ?", REGION_CODE);
		jdbcTemplate.update("""
			INSERT INTO region_code (code, display_name, level)
			VALUES (?, 'Test Country', 'COUNTRY')
			""", REGION_CODE);
	}

	@Test
	@DisplayName("Flyway V1 적용 후 Hibernate validate가 schema를 변경하지 않고 시작된다")
	void startsWithFlywaySchemaValidationOnly() {
		Integer successfulV1 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '1' AND success
			""", Integer.class);
		Integer applicationTableCount = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM information_schema.tables
			WHERE table_schema = 'public'
			  AND table_type = 'BASE TABLE'
			  AND table_name NOT IN ('flyway_schema_history', 'spatial_ref_sys')
			""", Integer.class);

		assertThat(successfulV1).isEqualTo(1);
		assertThat(applicationTableCount).isEqualTo(26);
	}

	@Test
	@DisplayName("Account를 저장하고 identity ID와 auditing 시각을 포함해 다시 조회한다")
	void savesAndFindsAccountThroughDomainPort() {
		Account saved = accountRepository.save(Account.create(
			AccountRole.USER,
			REGION_CODE,
			"ko-KR",
			"Asia/Seoul",
			"qello-user"
		));

		Account found = accountRepository.findById(saved.getId()).orElseThrow();

		assertThat(saved.getId()).isPositive();
		assertThat(saved.getCreatedAt()).isEqualTo(FIRST_AUDIT_TIME);
		assertThat(saved.getUpdatedAt()).isEqualTo(FIRST_AUDIT_TIME);
		assertThat(found).usingRecursiveComparison().isEqualTo(saved);
	}

	@Test
	@DisplayName("enum은 문자열로 저장되고 순차 갱신은 createdAt을 보존하며 updatedAt을 전진시킨다")
	void mapsEnumsAndUpdatesAuditTimestamp() {
		Account saved = accountRepository.save(Account.create(
			AccountRole.OPERATOR,
			REGION_CODE,
			"ko-KR",
			"Asia/Seoul",
			"before"
		));

		assertThat(rawString(saved.getId(), "role")).isEqualTo("OPERATOR");
		assertThat(rawString(saved.getId(), "status")).isEqualTo("ACTIVE");
		assertThat(rawInstant(saved.getId(), "created_at")).isEqualTo(FIRST_AUDIT_TIME);

		Instant blockedAt = FIRST_AUDIT_TIME.plus(Duration.ofMinutes(5));
		clock.setInstant(blockedAt);
		Account blocked = accountRepository.save(
			saved.updateProfile(REGION_CODE, "en-US", "UTC", "after")
				.changeStatus(AccountStatus.BLOCKED, null));

		assertThat(blocked.getStatus()).isEqualTo(AccountStatus.BLOCKED);
		assertThat(blocked.getCreatedAt()).isEqualTo(FIRST_AUDIT_TIME);
		assertThat(blocked.getUpdatedAt()).isEqualTo(blockedAt);
		assertThat(rawString(saved.getId(), "status")).isEqualTo("BLOCKED");

		Instant deletedAt = blockedAt.plus(Duration.ofMinutes(5));
		clock.setInstant(deletedAt);
		Account deleted = accountRepository.save(
			blocked.changeStatus(AccountStatus.DELETED, deletedAt));

		assertThat(deleted.getUpdatedAt()).isEqualTo(deletedAt);
		assertThat(rawString(saved.getId(), "status")).isEqualTo("DELETED");
		assertThat(rawInstant(saved.getId(), "created_at")).isEqualTo(FIRST_AUDIT_TIME);
		assertThat(rawInstant(saved.getId(), "updated_at")).isEqualTo(deletedAt);
		assertThat(rawInstant(saved.getId(), "deleted_at")).isEqualTo(deletedAt);
	}

	@Test
	@DisplayName("Account FK와 check constraint 위반은 저장 또는 flush 시점에 거절된다")
	void rejectsForeignKeyAndCheckConstraintViolations() {
		Account missingRegion = Account.create(
			AccountRole.USER,
			"UNKNOWN-REGION",
			"ko-KR",
			"Asia/Seoul",
			"missing-region"
		);

		assertThatThrownBy(() -> accountRepository.save(missingRegion))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO user_account (coarse_region_code, locale, timezone, nickname)
			VALUES (?, 'ko-KR', 'Asia/Seoul', '   ')
			""", REGION_CODE))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO user_account (
				status, coarse_region_code, locale, timezone, nickname
			)
			VALUES ('DELETED', ?, 'ko-KR', 'Asia/Seoul', 'deleted-without-time')
			""", REGION_CODE))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("같은 transaction의 두 Account 중 하나가 실패하면 정상 insert도 rollback된다")
	void rollsBackWholeTransactionAfterConstraintFailure() {
		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			accountRepository.save(Account.create(
				AccountRole.USER,
				REGION_CODE,
				"ko-KR",
				"Asia/Seoul",
				"must-rollback"
			));
			accountRepository.save(Account.create(
				AccountRole.USER,
				"UNKNOWN-REGION",
				"ko-KR",
				"Asia/Seoul",
				"invalid"
			));
		})).isInstanceOf(DataIntegrityViolationException.class);

		Integer remaining = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM user_account
			WHERE nickname = 'must-rollback'
			""", Integer.class);

		assertThat(remaining).isZero();
	}

	private String rawString(long accountId, String column) {
		return jdbcTemplate.queryForObject(
			"SELECT " + column + " FROM user_account WHERE id = ?",
			String.class,
			accountId
		);
	}

	private Instant rawInstant(long accountId, String column) {
		return jdbcTemplate.queryForObject(
			"SELECT " + column + " FROM user_account WHERE id = ?",
			(resultSet, rowNumber) -> resultSet
				.getObject(1, OffsetDateTime.class)
				.toInstant(),
			accountId
		);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClockConfiguration {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(FIRST_AUDIT_TIME, ZoneOffset.UTC);
		}

	}

	static final class MutableClock extends Clock {

		private final AtomicReference<Instant> current;
		private final ZoneId zone;

		private MutableClock(Instant initial, ZoneId zone) {
			this(new AtomicReference<>(initial), zone);
		}

		private MutableClock(AtomicReference<Instant> current, ZoneId zone) {
			this.current = current;
			this.zone = zone;
		}

		void setInstant(Instant instant) {
			current.set(instant);
		}

		@Override
		public ZoneId getZone() {
			return zone;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return new MutableClock(current, zone);
		}

		@Override
		public Instant instant() {
			return current.get();
		}

	}

}
