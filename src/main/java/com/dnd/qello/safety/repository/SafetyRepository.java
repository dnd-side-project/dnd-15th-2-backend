package com.dnd.qello.safety.repository;

import java.time.Instant;
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
}
