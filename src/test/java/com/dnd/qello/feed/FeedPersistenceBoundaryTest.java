package com.dnd.qello.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.feed.repository.jdbc.sql.FeedScopeSql;
import com.dnd.qello.feed.repository.jdbc.sql.InboxQuerySql;
import com.dnd.qello.feed.repository.jdbc.sql.PostAnswerQuerySql;
import com.dnd.qello.feed.service.InboxQueryService;

/**
 * Created at: 2026-08-06T14:30:00+09:00
 * Source scenario: TEST-PLAN-GH-67-INBOX-SENT-POST-UNIT-006 through UNIT-008,
 * TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS (2026-08-08 개정 반영),
 * TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-UNIT-001 through UNIT-003
 */
class FeedPersistenceBoundaryTest {

	@Test
	@DisplayName("feed view와 repository port는 Spring, JPA 구현에 의존하지 않는다")
	void viewsAndPortsRemainIndependent() throws IOException {
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello/feed/view"))) {
			assertThat(paths.filter(path -> path.toString().endsWith(".java")).map(this::read))
				.allMatch(source -> !source.contains("jakarta.persistence") && !source.contains("org.springframework"));
		}
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello/feed/repository"))) {
			List<String> ports = paths.filter(path -> path.toString().endsWith(".java"))
				.filter(path -> !path.toString().contains("/jdbc/"))
				.map(this::read).toList();
			assertThat(ports).allMatch(source -> !source.contains("jakarta.persistence")
				&& !source.contains("org.springframework.data"));
		}
	}

	/** feed 자신의 repository.jdbc 서브패키지(예: sql/)는 경계 위반이 아니므로 feature 이름까지 포함해 매칭한다. */
	private static final List<String> OTHER_FEATURES =
		List.of("account", "answer", "auth", "direction", "notification", "question", "safety");

	@Test
	@DisplayName("feed는 다른 feature의 JPA Entity와 JDBC 구현을 직접 참조하지 않는다")
	void feedDoesNotReachIntoOtherFeatureImplementations() throws IOException {
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello/feed"))) {
			assertThat(paths.filter(path -> path.toString().endsWith(".java")).map(this::read))
				.allMatch(source -> OTHER_FEATURES.stream().noneMatch(feature ->
					source.contains(feature + ".repository.jdbc.") || source.contains(feature + ".repository.jpa."))
					&& !source.contains("JpaEntity"));
		}
	}

	@Test
	@DisplayName("수신함 projection은 답변 수와 공감 수를 노출한다 — 2026-08-07 개정(ADR 0002)으로 답변이 수신 자격자 전원에게 공개됐다")
	void inboxProjectionsExposeAnswerAndReactionCounts() throws IOException {
		String source = read(Path.of("src/main/java/com/dnd/qello/feed/view/InboxCard.java"));

		assertThat(source).contains("answerCount").contains("reactionCount").contains("unreadAnswerCount");
	}

	@Test
	@DisplayName("공통 수신 열람 정책은 ANSWERED 예외와 명시적 만료 시각을 표현한다")
	void recipientViewPolicyUsesExplicitAtAndPreservesAnsweredAfterExpiry() {
		assertThat(FeedScopeSql.RECIPIENT_VIEW_ELIGIBILITY)
			.contains("pr.status = 'ANSWERED'")
			.contains("pr.status IN ('AVAILABLE','DISCOVERED','OPENED','SKIP_PENDING')")
			.contains("dp.expires_at > :at")
			.doesNotContain("CURRENT_TIMESTAMP")
			.doesNotContain("clock_timestamp()");
	}

	@Test
	@DisplayName("상세와 답변 열람 SQL은 같은 공통 수신 열람 정책을 사용한다")
	void detailAndAnswerQueriesShareRecipientViewPolicy() throws IOException {
		String inboxSql = read(Path.of("src/main/java/com/dnd/qello/feed/repository/jdbc/sql/InboxQuerySql.java"));
		String answerSql = read(Path.of("src/main/java/com/dnd/qello/feed/repository/jdbc/sql/PostAnswerQuerySql.java"));

		assertThat(inboxSql).contains("FeedScopeSql.ACTIVE_POST_VISIBILITY")
			.contains("FeedScopeSql.RECIPIENT_VIEW_ELIGIBILITY");
		assertThat(answerSql).contains("FeedScopeSql.RECIPIENT_VIEW_ELIGIBILITY");
		assertThat(InboxQuerySql.SCOPE_FILTER).contains(FeedScopeSql.ACTIVE_POST_VISIBILITY.trim());
		assertThat(InboxQuerySql.DETAIL_SCOPE_FILTER).contains(FeedScopeSql.ACTIVE_POST_VISIBILITY.trim());
		assertThat(PostAnswerQuerySql.CAN_VIEW_ANSWERS_SQL)
			.contains(FeedScopeSql.RECIPIENT_VIEW_ELIGIBILITY);
		assertThat(inboxSql).doesNotContain("CURRENT_TIMESTAMP");
		assertThat(answerSql).doesNotContain("CURRENT_TIMESTAMP");
	}

	@Test
	@DisplayName("상세 조회 계약은 조회 시각을 받고 빈 결과를 Optional로 반환한다")
	void detailContractRequiresAtAndOptionalResult() throws NoSuchMethodException {
		var detail = InboxQueryService.class.getDeclaredMethod("detail", long.class, long.class, Instant.class);

		assertThat(detail.getReturnType()).isEqualTo(java.util.Optional.class);
	}

	private String read(Path path) {
		try { return Files.readString(path); }
		catch (IOException exception) { throw new IllegalStateException(exception); }
	}
}
