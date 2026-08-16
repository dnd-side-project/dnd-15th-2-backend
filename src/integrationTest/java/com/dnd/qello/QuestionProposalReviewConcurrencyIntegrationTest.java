package com.dnd.qello;

/**
 * Created at: 2026-08-17T01:10:00+09:00
 * Source scenario: TEST-PLAN-GH-145-QUESTION-PROPOSAL-NOTIFICATION-INT-001,
 * INT-002, INT-005
 */

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.fanout.RecipientNotificationFanOutWorker;
import com.dnd.qello.question.domain.AnswerFormat;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.domain.QuestionProposalStatus;
import com.dnd.qello.question.repository.QuestionProposalRepository;
import com.dnd.qello.question.service.QuestionReviewService;

/**
 * 두 운영자가 같은 제안을 동시에 판정할 때의 최종 상태를 검증한다.
 *
 * <p>{@code QuestionReviewService.publishReviewed()}는 dedupKey를 먼저 조회한 뒤
 * 삽입하는 TOCTOU 구조라, 애플리케이션 수준 중복 억제만으로는 동시 판정을 막지
 * 못한다. 실제 방어선이 {@code uq_outbox_event_dedup} 제약인지 확인하는 것이 이
 * 클래스의 목적이다.</p>
 */
@SpringBootTest
@ActiveProfiles({"test", "question-proposal-concurrency"})
class QuestionProposalReviewConcurrencyIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION_CODE = "TEST-QUESTION-145";
	private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
	private static final String WORKER_OWNER = "question-proposal-concurrency-worker";

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private QuestionProposalRepository proposalRepository;

	@Autowired
	private QuestionReviewService reviewService;

	@Autowired
	private RecipientNotificationFanOutWorker fanOutWorker;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void resetDatabase() {
		jdbc.update("DELETE FROM outbox_event WHERE aggregate_type = 'QUESTION_PROPOSAL'");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM question_proposal_review");
		jdbc.update("DELETE FROM question_proposal");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION_CODE);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION_CODE);
		jdbc.update("DELETE FROM region_code WHERE code = 'KR'");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY'), (?, 'KR', 'Question Concurrency Region', 'REGION')
			""", REGION_CODE);
	}

	@Test
	@DisplayName("두 운영자가 같은 제안을 동시에 반려해도 판정 이력과 알림 이벤트는 하나씩만 남는다")
	void concurrentRejectionCommitsOnlyOnce() throws Exception {
		long proposalId = underReviewProposal("동시 반려 대상");
		long firstReviewer = createAccount("concurrent-reject-a").getId();
		long secondReviewer = createAccount("concurrent-reject-b").getId();

		int succeeded = runConcurrently(
			() -> reviewService.reject(proposalId, firstReviewer, "먼저 도착한 사유", NOW),
			() -> reviewService.reject(proposalId, secondReviewer, "나중에 도착한 사유", NOW));

		assertThat(succeeded).isEqualTo(1);
		assertThat(proposalRepository.findById(proposalId).orElseThrow().getStatus())
			.isEqualTo(QuestionProposalStatus.REJECTED);
		assertThat(countReviews(proposalId)).isEqualTo(1);
		assertThat(countOutboxEvents(proposalId)).isEqualTo(1);
	}

	@Test
	@DisplayName("두 운영자가 같은 제안을 동시에 승인해도 승인 질문과 알림 이벤트는 하나씩만 남는다")
	void concurrentApprovalCommitsOnlyOnce() throws Exception {
		long proposalId = underReviewProposal("동시 승인 대상");
		long firstReviewer = createAccount("concurrent-approve-a").getId();
		long secondReviewer = createAccount("concurrent-approve-b").getId();
		Instant activeFrom = NOW.plusSeconds(3600);
		Instant activeUntil = NOW.plusSeconds(7200);

		int succeeded = runConcurrently(
			() -> reviewService.approve(proposalId, firstReviewer, AnswerFormat.TEXT, activeFrom, activeUntil, NOW),
			() -> reviewService.approve(proposalId, secondReviewer, AnswerFormat.TEXT, activeFrom, activeUntil, NOW));

		assertThat(succeeded).isEqualTo(1);
		assertThat(proposalRepository.findById(proposalId).orElseThrow().getStatus())
			.isEqualTo(QuestionProposalStatus.APPROVED);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM approved_question WHERE source_proposal_id = ?", Integer.class, proposalId))
			.isEqualTo(1);
		assertThat(countReviews(proposalId)).isEqualTo(1);
		assertThat(countOutboxEvents(proposalId)).isEqualTo(1);
	}

	@Test
	@DisplayName("기존 fan-out worker는 QUESTION_PROPOSAL_REVIEWED를 claim하지 않고 PENDING으로 남긴다")
	void fanOutWorkerDoesNotClaimQuestionProposalEvent() {
		long proposalId = underReviewProposal("fan-out 미소비 대상");
		long reviewerId = createAccount("fanout-reviewer").getId();
		reviewService.reject(proposalId, reviewerId, "정책에 맞지 않습니다", NOW);

		RecipientNotificationFanOutWorker.BatchResult result = fanOutWorker.processBatch(
			new RecipientNotificationFanOutWorker.BatchCommand(10, WORKER_OWNER, NOW, NOW.plusSeconds(60),
				new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(attempt))));

		assertThat(result.claimed()).isZero();
		assertThat(jdbc.queryForObject("""
			SELECT status FROM outbox_event
			WHERE aggregate_type = 'QUESTION_PROPOSAL' AND aggregate_id = ?
			""", String.class, proposalId)).isEqualTo("PENDING");
		assertThat(jdbc.queryForObject("""
			SELECT lease_owner FROM outbox_event
			WHERE aggregate_type = 'QUESTION_PROPOSAL' AND aggregate_id = ?
			""", String.class, proposalId)).isNull();
	}

	/**
	 * 두 작업을 같은 시점에 출발시키고 성공한 개수를 센다. 실패한 쪽의 예외는
	 * 삼키지 않고 개수 판정에만 사용한다 — 어느 예외가 나오는지는 시나리오마다
	 * 다르므로(도메인 충돌 또는 DB 제약 위반) 여기서 타입을 고정하지 않는다.
	 */
	private int runConcurrently(Runnable first, Runnable second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Boolean>> futures = List.of(
				executor.submit(afterSignal(first, ready, start)),
				executor.submit(afterSignal(second, ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			int succeeded = 0;
			for (Future<Boolean> future : futures) {
				if (future.get(10, TimeUnit.SECONDS)) {
					succeeded++;
				}
			}
			return succeeded;
		} finally {
			executor.shutdownNow();
		}
	}

	private Callable<Boolean> afterSignal(Runnable action, CountDownLatch ready, CountDownLatch start) {
		return () -> {
			ready.countDown();
			start.await(5, TimeUnit.SECONDS);
			try {
				action.run();
				return true;
			} catch (RuntimeException expectedUnderContention) {
				return false;
			}
		};
	}

	private long underReviewProposal(String text) {
		long proposerId = createAccount("proposer-" + text.hashCode()).getId();
		QuestionProposal draft = proposalRepository.save(QuestionProposal.create(proposerId, text));
		reviewService.submit(draft.getId(), NOW);
		reviewService.startReview(draft.getId());
		return draft.getId();
	}

	private Integer countReviews(long proposalId) {
		return jdbc.queryForObject(
			"SELECT count(*) FROM question_proposal_review WHERE proposal_id = ?", Integer.class, proposalId);
	}

	private Integer countOutboxEvents(long proposalId) {
		return jdbc.queryForObject("""
			SELECT count(*) FROM outbox_event
			WHERE aggregate_type = 'QUESTION_PROPOSAL' AND aggregate_id = ?
			""", Integer.class, proposalId);
	}

	private Account createAccount(String nickname) {
		return accountRepository.save(Account.createUser("KR", REGION_CODE, "ko-KR", "Asia/Seoul", nickname));
	}
}
