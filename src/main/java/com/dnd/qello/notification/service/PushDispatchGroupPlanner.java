package com.dnd.qello.notification.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.push.group.PushDispatchGroup;
import com.dnd.qello.notification.push.group.PushDispatchGroupStatus;
import com.dnd.qello.notification.push.group.PushGroupingCandidate;
import com.dnd.qello.notification.push.policy.PushGroupingPolicy;
import com.dnd.qello.notification.push.policy.PushGroupingPolicy.GroupingDecision;
import com.dnd.qello.notification.push.policy.PushGroupingPolicy.GroupingMode;
import com.dnd.qello.notification.push.policy.PushGroupingPolicy.OpenGroup;
import com.dnd.qello.notification.repository.PushDispatchGroupRepository;

/** 미편입 notification을 수신자·종류별 durable group member로 넣는다. */
public class PushDispatchGroupPlanner {

	private final PushDispatchGroupRepository repository;
	private final PushGroupingPolicy groupingPolicy;

	public PushDispatchGroupPlanner(
		PushDispatchGroupRepository repository, PushGroupingPolicy groupingPolicy) {
		if (repository == null || groupingPolicy == null) {
			throw new IllegalArgumentException("group planner 의존성은 필수입니다");
		}
		this.repository = repository;
		this.groupingPolicy = groupingPolicy;
	}

	@Transactional
	public int collectUngrouped(int limit, Instant at) {
		if (limit <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "limit",
				"limit은 양수여야 합니다.");
		}
		if (at == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "at",
				"편입 시각은 필수입니다.");
		}
		List<PushGroupingCandidate> candidates = repository.lockUngrouped(limit, at);
		for (PushGroupingCandidate candidate : candidates) {
			if (unusableRecommendation(candidate)) {
				continue;
			}
			repository.acquireGroupingLock(candidate.recipientId(), candidate.type());
			repository.closeExpiredCollecting(candidate.recipientId(), candidate.type(), at);
			PushDispatchGroup group = resolveOrCreate(candidate, at);
			if (group != null) {
				repository.addMember(group.id(), candidate.notificationId(), at);
			}
		}
		return candidates.size();
	}

	private PushDispatchGroup resolveOrCreate(PushGroupingCandidate candidate, Instant at) {
		GroupingDecision decision = groupingPolicy.decide(candidate.type(), candidate.recipientId(),
			candidate.notificationId(), candidate.recommendationCycleId(), candidate.createdAt());
		if (decision.mode() == GroupingMode.WINDOWED) {
			Optional<PushDispatchGroup> open = repository.findCollectingForUpdate(
				candidate.recipientId(), candidate.type(), at);
			if (open.isPresent()) {
				return joining(open.get(), candidate);
			}
			return joining(repository.save(newGroup(candidate, decision, at)), candidate);
		}
		return repository.save(newGroup(candidate, decision, at));
	}

	private PushDispatchGroup joining(PushDispatchGroup group, PushGroupingCandidate candidate) {
		if (groupingPolicy.joins(
			new OpenGroup(group.recipientId(), group.notificationType(), group.collectUntil()),
			candidate.type(), candidate.recipientId(), candidate.createdAt())) {
			return group;
		}
		return null;
	}

	private static boolean unusableRecommendation(PushGroupingCandidate candidate) {
		return candidate.type() == NotificationType.QUESTION_RECOMMENDED
			&& (candidate.recommendationCycleId() == null || candidate.recommendationCycleId() <= 0);
	}

	private static PushDispatchGroup newGroup(
		PushGroupingCandidate candidate, GroupingDecision decision, Instant at) {
		PushDispatchGroupStatus status = decision.mode() == GroupingMode.WINDOWED
			? PushDispatchGroupStatus.COLLECTING
			: PushDispatchGroupStatus.PENDING;
		return new PushDispatchGroup(
			null,
			candidate.recipientId(),
			candidate.type(),
			decision.aggregationKey(),
			status,
			decision.windowStartedAt(),
			decision.collectUntil(),
			decision.policyExpiresAt(),
			0,
			decision.collectUntil(),
			null,
			null,
			null,
			at,
			null);
	}
}
