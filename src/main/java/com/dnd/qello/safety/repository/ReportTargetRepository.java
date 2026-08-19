package com.dnd.qello.safety.repository;

import java.time.Instant;
import java.util.Optional;

import com.dnd.qello.safety.domain.ReportTargetSnapshot;

public interface ReportTargetRepository {

	Optional<ReportTargetSnapshot> findViewableAnswer(long answerId, long viewerId, Instant at);

	Optional<ReportTargetSnapshot> findViewablePost(long postId, long viewerId, Instant at);

	Optional<ReportTargetSnapshot> findViewableUser(long targetUserId, long viewerId);
}
