/**
 * Created at: 2026-08-18T21:30:00+09:00
 * Source scenario: TEST-PLAN-GH-113-FILTERING-OBSERVABILITY-AND-GATE-INT-001, INT-002,
 *                  INT-004 ~ INT-006, INT-017
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;

import com.dnd.qello.filtering.audit.OperatorActionAuditRecorder;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.OperatorActionAudit;
import com.dnd.qello.filtering.domain.OperatorActionTargetType;
import com.dnd.qello.filtering.domain.OperatorActionType;
import com.dnd.qello.filtering.domain.OperatorReason;
import com.dnd.qello.filtering.repository.OperatorActionAuditRepository;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;

// #113: 운영자 권한 변경마다 actor·reason·policy version·시간이 남는지, 감사가
// 결정과 같은 트랜잭션에 묶이는지, 원장이 append-only인지를 실제 PostgreSQL에서
// 검증한다.
@SpringBootTest
@ActiveProfiles("test")
class OperatorActionAuditIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final long OPERATOR_USER_ID = 9L;
	private static final OperatorReason REASON = new OperatorReason("POLICY_UPDATE", "정책 개정 반영");
	private static final String MODEL_SNAPSHOT = "omni-moderation-2026-08-01";

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private FilterReleaseRegistryService releaseRegistryService;
	@Autowired
	private OperatorActionAuditRepository auditRepository;
	@Autowired
	private OperatorActionAuditRecorder auditRecorder;

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM operator_action_audit");
		jdbc.update("DELETE FROM release_promotion_history");
		jdbc.update("DELETE FROM filter_job");
		jdbc.update("DELETE FROM filter_release");
	}

	@Test
	@DisplayName("INT-001: V20이 operator_action_audit 테이블과 제약·인덱스를 제공한다")
	void appliesOperatorActionAuditSchema() {
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM information_schema.columns
			WHERE table_name = 'operator_action_audit'
			  AND column_name IN ('operator_user_id', 'action_type', 'target_type', 'target_key',
			                      'reason_code', 'reason_text', 'policy_version', 'occurred_at')
			""", Integer.class)).isEqualTo(8);
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM pg_constraint
			WHERE conname IN ('ck_operator_action_audit_operator', 'ck_operator_action_audit_target_key',
			                  'ck_operator_action_audit_reason_code', 'ck_operator_action_audit_reason_text',
			                  'ck_operator_action_audit_policy_version', 'ck_operator_action_audit_action_type',
			                  'ck_operator_action_audit_target_type')
			""", Integer.class)).isEqualTo(7);
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM pg_indexes
			WHERE indexname IN ('operator_action_audit_target_idx', 'operator_action_audit_operator_idx')
			""", Integer.class)).isEqualTo(2);
	}

	@Test
	@DisplayName("INT-002: release 승격은 상태 전이와 감사 기록을 같은 트랜잭션으로 남긴다")
	void promotionRecordsAuditInSameTransaction() {
		long releaseId = promotedRelease();

		List<OperatorActionAudit> audits =
			auditRepository.findByTarget(OperatorActionTargetType.FILTER_RELEASE, String.valueOf(releaseId));

		assertThat(audits).extracting(OperatorActionAudit::actionType).containsExactly(
			OperatorActionType.RELEASE_MARK_OFFLINE_EVALUATED,
			OperatorActionType.RELEASE_DESIGNATE_SHADOW,
			OperatorActionType.RELEASE_DESIGNATE_CANARY,
			OperatorActionType.RELEASE_PROMOTE);
		assertThat(audits).allSatisfy(audit -> {
			assertThat(audit.operatorUserId()).isEqualTo(OPERATOR_USER_ID);
			assertThat(audit.reasonCode()).isEqualTo("POLICY_UPDATE");
			assertThat(audit.reasonText()).isEqualTo("정책 개정 반영");
			// release 자체가 정책 단위이므로 policy version은 release 식별자다.
			assertThat(audit.policyVersion()).isEqualTo("release:" + releaseId);
			assertThat(audit.occurredAt()).isNotNull();
		});
	}

	@Test
	@DisplayName("INT-004: 서로 다른 운영자 경로는 서로 다른 action_type으로 구분돼 남는다")
	void distinctOperatorPathsRecordDistinctActionTypes() {
		long first = promotedRelease();
		// rollback은 이미 내려간 release를 다시 올리는 경로다. 두 번째 release를
		// 승격해 first를 ROLLED_BACK으로 만든 뒤에야 호출할 수 있다.
		promotedRelease();
		releaseRegistryService.rollback(first, OPERATOR_USER_ID, REASON);

		List<String> actionTypes = jdbc.queryForList(
			"SELECT action_type FROM operator_action_audit WHERE target_key = ? ORDER BY id",
			String.class, String.valueOf(first));

		// 두 번째 release가 승격되면서 first가 내려간 사실도 first의 이력에 남아야
		// 한다. 남지 않으면 "누가 왜 이 release를 내렸는가"에 답할 수 없다.
		assertThat(actionTypes).containsExactly(
			"RELEASE_MARK_OFFLINE_EVALUATED", "RELEASE_DESIGNATE_SHADOW", "RELEASE_DESIGNATE_CANARY",
			"RELEASE_PROMOTE", "RELEASE_DEMOTED_BY_PROMOTION", "RELEASE_ROLLBACK");
	}

	@Test
	@DisplayName("INT-017: 정의되지 않은 action_type과 공백 reason_code는 DB 제약이 거절한다")
	void databaseRejectsUndefinedEnumAndBlankReasonCode() {
		assertThatThrownBy(() -> insertAudit("NOT_A_REAL_ACTION", "FILTER_RELEASE", "CODE"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertAudit("RELEASE_PROMOTE", "NOT_A_REAL_TARGET", "CODE"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertAudit("RELEASE_PROMOTE", "FILTER_RELEASE", "   "))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private void insertAudit(String actionType, String targetType, String reasonCode) {
		jdbc.update("""
			INSERT INTO operator_action_audit
				(operator_user_id, action_type, target_type, target_key, reason_code, reason_text,
				 policy_version, occurred_at)
			VALUES (1, ?, ?, '1', ?, '근거', 'v1', ?)
			""", actionType, targetType, reasonCode, java.sql.Timestamp.from(Instant.now()));
	}

	@Test
	@DisplayName("INT-005: 감사 기록은 트랜잭션 밖에서 호출할 수 없다")
	void auditCannotRunOutsideCallerTransaction() {
		// MANDATORY 전파라 자신만의 트랜잭션을 열지 않는다. 이 계약이 깨지면
		// "결정은 커밋됐는데 근거는 롤백된" 조합이 생길 수 있다.
		assertThatThrownBy(() -> auditRecorder.record(OPERATOR_USER_ID, OperatorActionType.RELEASE_PROMOTE,
			OperatorActionTargetType.FILTER_RELEASE, "1", REASON, "release:1"))
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(countAudits()).isZero();
	}

	@Test
	@DisplayName("INT-006: 감사 원장은 append-only이며 DB 제약이 빈 근거를 거절한다")
	void auditLedgerIsAppendOnlyAndRejectsBlankReason() {
		long first = promotedRelease();
		promotedRelease();
		int before = countAudits();

		// 같은 대상에 행위를 더 하면 덮어쓰지 않고 행이 쌓인다. rollback은 대상의
		// RELEASE_ROLLBACK과 내려간 release의 RELEASE_DEMOTED_BY_PROMOTION을 함께 남긴다.
		releaseRegistryService.rollback(first, OPERATOR_USER_ID, REASON);
		assertThat(countAudits()).isEqualTo(before + 2);

		assertThatThrownBy(() -> jdbc.update("""
			INSERT INTO operator_action_audit
				(operator_user_id, action_type, target_type, target_key, reason_code, reason_text,
				 policy_version, occurred_at)
			VALUES (1, 'RELEASE_PROMOTE', 'FILTER_RELEASE', '1', 'CODE', '   ', 'v1', ?)
			""", java.sql.Timestamp.from(Instant.now())))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private int countAudits() {
		return jdbc.queryForObject("SELECT count(*) FROM operator_action_audit", Integer.class);
	}

	private long promotedRelease() {
		FilterRelease candidate = releaseRegistryService.createCandidate(
			"norm-v1", "ruleset-v1", "category-map-v1", MODEL_SNAPSHOT);
		releaseRegistryService.markOfflineEvaluated(candidate.id(), OPERATOR_USER_ID, REASON);
		releaseRegistryService.designateShadow(candidate.id(), OPERATOR_USER_ID, REASON);
		releaseRegistryService.designateCanary(candidate.id(), OPERATOR_USER_ID, REASON);
		return releaseRegistryService.promote(candidate.id(), OPERATOR_USER_ID, REASON).id();
	}
}
