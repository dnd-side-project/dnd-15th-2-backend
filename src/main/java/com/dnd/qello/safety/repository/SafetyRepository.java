package com.dnd.qello.safety.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.safety.domain.ModerationReview;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.UserBlock;

public interface SafetyRepository {

	UserBlock block(UserBlock block);

	Optional<UserBlock> findBlock(long blockerId, long blockedId);

	UserBlock releaseBlock(long blockerId, long blockedId, Instant releasedAt);

	Report saveReport(Report report);

	Optional<Report> findReportById(long reportId);

	Optional<Report> findOpenReport(long reporterId, Long targetUserId, Long directionPostId, Long answerId);

	Report updateReport(Report report);

	ModerationReview saveReview(ModerationReview review);

	/** 같은 신고자가 이 대상에 대해 종결(ACTIONED/NO_VIOLATION)한 가장 최근 신고. 재신고 억제 판정에 쓴다(#154). */
	Optional<Report> findMostRecentClosedReport(
		long reporterId, Long targetUserId, Long directionPostId, Long answerId);

	/** rate limit 판정용. since 이후 이 신고자가 접수한 신고 수(#154). */
	int countReportsByReporterSince(long reporterId, Instant since);

	/**
	 * 같은 신고자의 동시 제출을 직렬화한다(트랜잭션 종료 시 자동 해제). rate limit
	 * count-then-insert와 findOpenReport-then-insert가 각각 별도 연산이라 생기는
	 * 경합을 이 잠금으로 없앤다 — 두 번째 트랜잭션은 첫 번째가 커밋한 뒤에야
	 * 진행되므로 이미 저장된 신고를 그대로 조회한다(#154 리뷰 후속).
	 */
	void acquireReporterSubmissionLock(long reporterId);

	/** 최신순 커서 페이지네이션. cursorCreatedAt·cursorId가 둘 다 null이면 처음부터(#154). */
	List<Report> findReportsByReporter(long reporterId, Instant cursorCreatedAt, Long cursorId, int limit);

	/** 사건 종결(#155) 시 신고자 수만큼 결과 알림을 내보내려면 사건에 묶인 신고를 모두 알아야 한다. */
	List<Report> findReportsByCaseId(long caseId);
}
