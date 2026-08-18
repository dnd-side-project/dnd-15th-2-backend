/**
 * Created at: 2026-08-04T12:00:00+09:00
 * Source scenario: TEST-PLAN-GH-48-ACCOUNT-PASSWORD-INT-001
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.repository.AccountRepository;

/**
 * Created at: 2026-08-04T12:00:00+09:00
 * Source scenario: TEST-PLAN-GH-48-ACCOUNT-PASSWORD-INT-001 through INT-006,
 * TEST-PLAN-GH-88-COUNTRY-ONBOARDING-INT-003
 */
@SpringBootTest
@ActiveProfiles({"test", "account-persistence"})
@Import(AccountPersistenceIntegrationTest.TestClockConfiguration.class)
class AccountPersistenceIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION_CODE = "TEST-COUNTRY";
	private static final Instant FIRST_AUDIT_TIME =
		Instant.parse("2026-08-03T09:00:00Z");
	private static final String OPERATOR_PASSWORD_HASH = "$2a$10$fixed-test-hash-value";

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void resetDatabaseAndClock() {
		clock.setInstant(FIRST_AUDIT_TIME);
		jdbcTemplate.update("DELETE FROM user_account");
		jdbcTemplate.update("DELETE FROM region_code WHERE code = ?", REGION_CODE);
		jdbcTemplate.update("DELETE FROM region_code WHERE code = 'KR'");
		jdbcTemplate.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY'), (?, 'KR', 'Test Region', 'REGION')
			""", REGION_CODE);
	}

	@Test
	@DisplayName("Flyway V1~V7 적용 후 Hibernate validate가 schema를 변경하지 않고 시작된다")
	void startsWithFlywaySchemaValidationOnly() {
		Integer successfulMigrations = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version IN ('1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11') AND success
			""", Integer.class);
		Integer applicationTableCount = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM information_schema.tables
			WHERE table_schema = 'public'
			  AND table_type = 'BASE TABLE'
			  AND table_name NOT IN ('flyway_schema_history', 'spatial_ref_sys')
			""", Integer.class);

		assertThat(successfulMigrations).isEqualTo(11);
		// V1~V4의 28개 + operator_credential + spring_session + spring_session_attributes + device_credential(V7).
		// V8·V9는 기존 테이블만 ALTER한다.
		// + filter_release/filter_job/filter_job_status_history/filter_decision/manual_review_case/appeal_case(V10)
		// + release_promotion_history(V11, filter_release는 V11에서 컬럼만 추가)
		// V12·V13은 기존 테이블만 ALTER한다(신규 테이블 없음).
		// + filter_release_retry_gate(V14, #108)
		// + snapshot_health/snapshot_health_probe_result/snapshot_emergency_migration_history(V15, #109)
		// + manual_review_priority_evaluation(V16, #110 — manual_review_case는 V10의 기존
		//   테이블에 컬럼만 추가한다)
		// + notification_event(V17, #111)
		// appeal_case는 V10의 기존 테이블에 V18(#112)이 컬럼만 추가한다(신규 테이블 없음).
		// + report_case/report_content_snapshot/report_case_event(V19, #153 — report는
		//   V1의 기존 테이블에 컬럼만 추가한다)
		// — 이 count는 flyway_schema_history를 version 1~11로만 필터링하므로 V14~V19
		// 자체는 마이그레이션 성공 여부에 포함되지 않지만, 애플리케이션 시작 시 전체
		// migration이 먼저 적용된 뒤 이 테스트가 실행되므로 V14~V19가 만든 테이블도
		// 이 count에 포함된다.
		// + operator_action_audit(V20, #113 — 운영자 행위 감사 원장)
		assertThat(applicationTableCount).isEqualTo(49);
	}

	@Test
	@DisplayName("일반 사용자 Account를 저장하고 identity ID와 auditing 시각을 포함해 다시 조회한다")
	void savesAndFindsUserAccountThroughDomainPort() {
		Account saved = accountRepository.save(Account.createUser("KR",
			REGION_CODE, "ko-KR", "Asia/Seoul", "qello-user"));

		Account found = accountRepository.findById(saved.getId()).orElseThrow();

		assertThat(saved.getId()).isPositive();
		assertThat(rawInstant(saved.getId(), "created_at")).isEqualTo(FIRST_AUDIT_TIME);
		assertThat(rawInstant(saved.getId(), "updated_at")).isEqualTo(FIRST_AUDIT_TIME);
		assertThat(found).usingRecursiveComparison().isEqualTo(saved);
	}

	@Test
	@DisplayName("관리자 Account는 자격증명 없이 저장되고 자격증명은 operator_credential에만 존재한다")
	void savesOperatorAccountWithoutCredentialColumn() {
		Account saved = accountRepository.save(Account.createOperator(
			REGION_CODE, "ko-KR", "Asia/Seoul", "qello-admin"));

		jdbcTemplate.update("""
			INSERT INTO operator_credential (user_id, login_id, password_hash)
			VALUES (?, 'qello-admin', ?)
			""", saved.getId(), OPERATOR_PASSWORD_HASH);

		String storedHash = jdbcTemplate.queryForObject(
			"SELECT password_hash FROM operator_credential WHERE user_id = ?",
			String.class, saved.getId());
		Integer passwordColumnsOnAccount = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM information_schema.columns
			WHERE table_schema = 'public'
			  AND table_name = 'user_account'
			  AND column_name = 'password_hash'
			""", Integer.class);

		assertThat(storedHash).isEqualTo(OPERATOR_PASSWORD_HASH);
		assertThat(storedHash).doesNotContain("qello-admin");
		assertThat(passwordColumnsOnAccount).isZero();
		assertThat(rawString(saved.getId(), "country_code")).isNull();
	}

	@Test
	@DisplayName("USER의 국가 NULL과 COUNTRY가 아닌 국가 참조는 DB 제약으로 거절된다")
	void rejectsUserWithoutCountryOrCountryLevelReference() {
		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO user_account
				(role, country_code, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', NULL, ?, 'ko-KR', 'Asia/Seoul', 'missing-country')
			""", REGION_CODE))
			.isInstanceOf(DataIntegrityViolationException.class);

		jdbcTemplate.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('ZZ', 'KR', 'Not a country', 'REGION')
			""");
		try {
			assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO user_account
					(role, country_code, coarse_region_code, locale, timezone, nickname)
				VALUES ('USER', 'ZZ', ?, 'ko-KR', 'Asia/Seoul', 'wrong-level')
				""", REGION_CODE))
				.isInstanceOf(DataIntegrityViolationException.class);
		} finally {
			jdbcTemplate.update("DELETE FROM region_code WHERE code = 'ZZ'");
		}
	}

	@Test
	@DisplayName("enum은 문자열로 저장되고 순차 갱신은 createdAt을 보존하며 updatedAt을 전진시킨다")
	void mapsEnumsAndUpdatesAuditTimestamp() {
		Account saved = accountRepository.save(Account.createOperator(
			REGION_CODE, "ko-KR", "Asia/Seoul", "before"));

		assertThat(rawString(saved.getId(), "role")).isEqualTo("OPERATOR");
		assertThat(rawString(saved.getId(), "status")).isEqualTo("ACTIVE");
		assertThat(rawInstant(saved.getId(), "created_at")).isEqualTo(FIRST_AUDIT_TIME);

		Instant blockedAt = FIRST_AUDIT_TIME.plus(Duration.ofMinutes(5));
		clock.setInstant(blockedAt);
		Account withNewProfile = accountRepository.updateProfile(
			saved.updateProfile(REGION_CODE, "en-US", "UTC", "after"));
		Account blocked = accountRepository.updateStatus(withNewProfile.block());

		assertThat(blocked.getStatus()).isEqualTo(AccountStatus.BLOCKED);
		assertThat(rawInstant(saved.getId(), "created_at")).isEqualTo(FIRST_AUDIT_TIME);
		assertThat(rawInstant(saved.getId(), "updated_at")).isEqualTo(blockedAt);
		assertThat(rawString(saved.getId(), "status")).isEqualTo("BLOCKED");

		Instant deletedAt = blockedAt.plus(Duration.ofMinutes(5));
		clock.setInstant(deletedAt);
		accountRepository.updateStatus(blocked.delete(deletedAt));

		assertThat(rawString(saved.getId(), "status")).isEqualTo("DELETED");
		assertThat(rawInstant(saved.getId(), "created_at")).isEqualTo(FIRST_AUDIT_TIME);
		assertThat(rawInstant(saved.getId(), "updated_at")).isEqualTo(deletedAt);
		assertThat(rawInstant(saved.getId(), "deleted_at")).isEqualTo(deletedAt);
	}

	@Test
	@DisplayName("기존 계정 수정은 Dirty Checking으로 반영되고 신규 row를 추가로 만들지 않는다")
	void updatesExistingAccountThroughDirtyCheckingWithoutExtraInsert() {
		Account saved = accountRepository.save(Account.createUser("KR",
			REGION_CODE, "ko-KR", "Asia/Seoul", "original"));

		accountRepository.updateProfile(saved.updateProfile(REGION_CODE, "ko-KR", "Asia/Seoul", "renamed"));

		Integer rowCount = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM user_account", Integer.class);
		String nickname = rawString(saved.getId(), "nickname");

		assertThat(rowCount).isEqualTo(1);
		assertThat(nickname).isEqualTo("renamed");
	}

	@Test
	@DisplayName("version은 저장 시 0에서 시작해 수정할 때마다 증가한다")
	void versionStartsAtZeroAndAdvancesOnEachUpdate() {
		Account saved = accountRepository.save(Account.createUser("KR",
			REGION_CODE, "ko-KR", "Asia/Seoul", "original"));

		assertThat(rawLong(saved.getId(), "version")).isZero();

		Account renamed = accountRepository.updateProfile(
			saved.updateProfile(REGION_CODE, "ko-KR", "Asia/Seoul", "renamed"));

		assertThat(rawLong(saved.getId(), "version")).isEqualTo(1L);

		accountRepository.updateStatus(renamed.block());

		assertThat(rawLong(saved.getId(), "version")).isEqualTo(2L);
	}

	@Test
	@DisplayName("먼저 커밋한 수정이 있으면 오래된 요청은 낙관적 잠금 충돌로 거절된다")
	void rejectsStaleUpdateWithOptimisticLockingFailure() {
		Account saved = accountRepository.save(Account.createUser("KR",
			REGION_CODE, "ko-KR", "Asia/Seoul", "original"));

		// 바깥 트랜잭션이 version 0을 읽어 둔 뒤, 다른 트랜잭션이 먼저 같은 행을 변경한다.
		assertThatThrownBy(() -> transactionTemplate.execute(status -> {
			Account loaded = accountRepository.findById(saved.getId()).orElseThrow();
			commitInSeparateTransaction(() -> jdbcTemplate.update(
				"UPDATE user_account SET nickname = ?, version = version + 1 WHERE id = ?",
				"other-request", saved.getId()));

			return accountRepository.updateProfile(
				loaded.updateProfile(REGION_CODE, "ko-KR", "Asia/Seoul", "stale"));
		})).isInstanceOf(OptimisticLockingFailureException.class);

		assertThat(rawString(saved.getId(), "nickname")).isEqualTo("other-request");
	}

	@Test
	@DisplayName("존재하지 않는 id를 수정하려 하면 404로 매핑되는 ACCOUNT_NOT_FOUND가 발생한다")
	void updatingMissingAccountFails() {
		Account saved = accountRepository.save(Account.createUser("KR",
			REGION_CODE, "ko-KR", "Asia/Seoul", "temp"));
		jdbcTemplate.update("DELETE FROM user_account WHERE id = ?", saved.getId());

		assertThatThrownBy(() -> accountRepository.updateProfile(
			saved.updateProfile(REGION_CODE, "ko-KR", "Asia/Seoul", "renamed")))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.ACCOUNT_NOT_FOUND);
		assertThatThrownBy(() -> accountRepository.updateStatus(saved.block()))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.ACCOUNT_NOT_FOUND);
		assertThat(AccountErrorCode.ACCOUNT_NOT_FOUND.httpStatus().value()).isEqualTo(404);
	}

	@Test
	@DisplayName("Account FK와 check constraint 위반은 저장 또는 flush 시점에 거절된다")
	void rejectsForeignKeyAndCheckConstraintViolations() {
		Account missingRegion = Account.createUser("KR",
			"UNKNOWN-REGION", "ko-KR", "Asia/Seoul", "missing-region");

		assertThatThrownBy(() -> accountRepository.save(missingRegion))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO user_account (country_code, coarse_region_code, locale, timezone, nickname)
			VALUES ('KR', ?, 'ko-KR', 'Asia/Seoul', '   ')
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
	@DisplayName("USER 계정에 운영자 자격증명을 붙이면 복합 FK가 거절한다")
	void rejectsCredentialOnUserAccount() {
		Account user = accountRepository.save(Account.createUser("KR",
			REGION_CODE, "ko-KR", "Asia/Seoul", "plain-user"));

		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO operator_credential (user_id, login_id, password_hash)
			VALUES (?, 'not-an-operator', ?)
			""", user.getId(), OPERATOR_PASSWORD_HASH))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("자격증명이 있는 계정을 USER로 강등하면 복합 FK가 거절한다")
	void rejectsRoleDemotionWhileCredentialExists() {
		Account operator = accountRepository.save(Account.createOperator(
			REGION_CODE, "ko-KR", "Asia/Seoul", "demote-target"));
		jdbcTemplate.update("""
			INSERT INTO operator_credential (user_id, login_id, password_hash)
			VALUES (?, 'demote-target', ?)
			""", operator.getId(), OPERATOR_PASSWORD_HASH);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"UPDATE user_account SET role = 'USER' WHERE id = ?", operator.getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("login_id는 유일하고 대문자와 공백만인 값을 거절한다")
	void rejectsInvalidLoginId() {
		Account first = accountRepository.save(Account.createOperator(
			REGION_CODE, "ko-KR", "Asia/Seoul", "first-operator"));
		Account second = accountRepository.save(Account.createOperator(
			REGION_CODE, "ko-KR", "Asia/Seoul", "second-operator"));
		jdbcTemplate.update("""
			INSERT INTO operator_credential (user_id, login_id, password_hash)
			VALUES (?, 'taken-login', ?)
			""", first.getId(), OPERATOR_PASSWORD_HASH);

		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO operator_credential (user_id, login_id, password_hash)
			VALUES (?, 'taken-login', ?)
			""", second.getId(), OPERATOR_PASSWORD_HASH))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO operator_credential (user_id, login_id, password_hash)
			VALUES (?, 'Mixed-Case', ?)
			""", second.getId(), OPERATOR_PASSWORD_HASH))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("계정을 지우면 자격증명도 함께 사라진다")
	void cascadesCredentialDeletionWithAccount() {
		Account operator = accountRepository.save(Account.createOperator(
			REGION_CODE, "ko-KR", "Asia/Seoul", "cascade-target"));
		jdbcTemplate.update("""
			INSERT INTO operator_credential (user_id, login_id, password_hash)
			VALUES (?, 'cascade-target', ?)
			""", operator.getId(), OPERATOR_PASSWORD_HASH);

		jdbcTemplate.update("DELETE FROM user_account WHERE id = ?", operator.getId());

		Integer remaining = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM operator_credential WHERE user_id = ?",
			Integer.class, operator.getId());
		assertThat(remaining).isZero();
	}

	@Test
	@DisplayName("같은 transaction의 두 Account 중 하나가 실패하면 정상 insert도 rollback된다")
	void rollsBackWholeTransactionAfterConstraintFailure() {
		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			accountRepository.save(Account.createUser("KR",
				REGION_CODE, "ko-KR", "Asia/Seoul", "must-rollback"));
			accountRepository.save(Account.createUser("KR",
				"UNKNOWN-REGION", "ko-KR", "Asia/Seoul", "invalid"));
		})).isInstanceOf(DataIntegrityViolationException.class);

		Integer remaining = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM user_account
			WHERE nickname = 'must-rollback'
			""", Integer.class);

		assertThat(remaining).isZero();
	}

	private Long rawLong(long id, String column) {
		return jdbcTemplate.queryForObject(
			"SELECT " + column + " FROM user_account WHERE id = ?", Long.class, id);
	}

	private void commitInSeparateTransaction(Runnable work) {
		TransactionTemplate separate = new TransactionTemplate(transactionManager);
		separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		separate.executeWithoutResult(status -> work.run());
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
