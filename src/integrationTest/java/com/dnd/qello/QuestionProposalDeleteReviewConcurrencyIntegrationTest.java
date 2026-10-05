/**
 * Created at: 2026-10-05T18:10:06+09:00
 * Source scenario: TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-INT-008
 */
package com.dnd.qello;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.question.domain.AnswerFormat;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.error.QuestionErrorCode;
import com.dnd.qello.question.error.QuestionException;
import com.dnd.qello.question.service.QuestionProposalApplicationService;
import com.dnd.qello.question.service.QuestionReviewService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사용자 삭제와 운영자 승인이 같은 제안에 동시에 들어올 때 행 잠금으로 직렬화되는지 검증한다.
 *
 * <p>
 * 삭제는 상태와 관계없이 성공하므로 항상 성공해야 한다. 승인은 삭제보다 먼저 잠금을 잡으면 성공하고, 늦으면 409로 거부된다. 어느
 * 순서든 삭제 시각이 승인 저장에 덮여 사라지면 안 된다.
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
class QuestionProposalDeleteReviewConcurrencyIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-QUESTION-310-CC";
	private static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private AccountRepository accountRepository;
	@Autowired
	private QuestionProposalApplicationService applicationService;
	@Autowired
	private QuestionReviewService reviewService;

	private long proposerId;
	private long reviewerId;

	@BeforeEach
	void setUp() {
		cleanup();
		jdbc.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES ('KR', NULL, 'Korea', 'COUNTRY')
				ON CONFLICT (code) DO NOTHING
				""");
		jdbc.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES (?, 'KR', 'GH310 Concurrency', 'REGION')
				ON CONFLICT (code) DO NOTHING
				""", REGION);
		proposerId = accountRepository.save(
				Account.createUser("KR", REGION, "ko-KR", "Asia/Seoul", "gh310-cc-proposer")).getId();
		reviewerId = accountRepository.save(
				Account.createUser("KR", REGION, "ko-KR", "Asia/Seoul", "gh310-cc-reviewer")).getId();
	}

	@AfterEach
	void tearDown() {
		cleanup();
	}

	@RepeatedTest(5)
	@DisplayName("삭제와 승인이 동시에 실행되면 삭제는 성공하고 승인 결과와 DB 상태가 같은 순서를 가리킨다")
	void deleteAndApproveSerialize() throws Exception {
		QuestionProposal proposal = applicationService.submit(proposerId, "동시 삭제·승인 대상");
		reviewService.startReview(proposal.getId());
		long proposalId = proposal.getId();

		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Boolean> delete = executor.submit(awaiting(start, () -> {
				applicationService.delete(proposerId, proposalId);
				return true;
			}));
			Future<Boolean> approve = executor.submit(awaiting(start, () -> {
				try {
					reviewService.approve(proposalId, reviewerId, AnswerFormat.TEXT,
							NOW.plusSeconds(3600), NOW.plusSeconds(7200), NOW);
					return true;
				} catch (QuestionException exception) {
					assertThat(exception.getErrorCode()).isEqualTo(QuestionErrorCode.INVALID_PROPOSAL_STATUS);
					return false;
				}
			}));
			start.countDown();

			assertThat(delete.get(30, TimeUnit.SECONDS)).isTrue();
			boolean approved = approve.get(30, TimeUnit.SECONDS);

			assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM question_proposal WHERE id = ?",
					Boolean.class, proposalId)).isTrue();
			long reviews = count("SELECT count(*) FROM question_proposal_review WHERE proposal_id = ?", proposalId);
			long approvedQuestions = count("SELECT count(*) FROM approved_question WHERE source_proposal_id = ?",
					proposalId);
			String status = jdbc.queryForObject("SELECT status FROM question_proposal WHERE id = ?", String.class,
					proposalId);
			if (approved) {
				assertThat(status).isEqualTo("APPROVED");
				assertThat(reviews).isEqualTo(1L);
				assertThat(approvedQuestions).isEqualTo(1L);
			} else {
				assertThat(status).isEqualTo("UNDER_REVIEW");
				assertThat(reviews).isZero();
				assertThat(approvedQuestions).isZero();
			}
		} finally {
			executor.shutdownNow();
		}
	}

	private static <T> Callable<T> awaiting(CountDownLatch start, Callable<T> task) {
		return () -> {
			start.await();
			return task.call();
		};
	}

	private long count(String sql, long argument) {
		return jdbc.queryForObject(sql, Long.class, argument);
	}

	private void cleanup() {
		String proposals = "SELECT id FROM question_proposal WHERE proposer_id IN "
				+ "(SELECT id FROM user_account WHERE coarse_region_code = '" + REGION + "')";
		jdbc.update("DELETE FROM outbox_event WHERE aggregate_type = 'QUESTION_PROPOSAL' AND aggregate_id IN ("
				+ proposals + ")");
		jdbc.update("DELETE FROM approved_question WHERE source_proposal_id IN (" + proposals + ")");
		jdbc.update("DELETE FROM question_proposal_review WHERE proposal_id IN (" + proposals + ")");
		jdbc.update("DELETE FROM question_proposal WHERE id IN (" + proposals + ")");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
	}
}
