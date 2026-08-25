package com.dnd.qello.notification.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.dnd.qello.notification.config.PushPolicyProperties;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.push.PushDeliveryTerminalResult;
import com.dnd.qello.notification.push.group.ClaimedPushDeviceDispatch;
import com.dnd.qello.notification.push.group.ClaimedPushDispatchGroup;
import com.dnd.qello.notification.push.group.PushBudgetReservation;
import com.dnd.qello.notification.push.group.PushDispatchGroup;
import com.dnd.qello.notification.push.group.PushDispatchGroupContext;
import com.dnd.qello.notification.push.group.PushDispatchGroupStatus;
import com.dnd.qello.notification.push.group.PushGroupingCandidate;

public interface PushDispatchGroupRepository {

	List<PushGroupingCandidate> lockUngrouped(int limit, Instant at);

	void acquireGroupingLock(long recipientId, NotificationType type);

	int closeExpiredCollecting(long recipientId, NotificationType type, Instant at);

	Optional<PushDispatchGroup> findCollectingForUpdate(
		long recipientId, NotificationType type, Instant at);

	PushDispatchGroup save(PushDispatchGroup group);

	boolean addMember(long groupId, long notificationId, Instant at);

	List<ClaimedPushDispatchGroup> claimDueGroups(
		int limit, Instant now, Instant leaseUntil);

	boolean transition(long groupId, int generation, PushDispatchGroupStatus status,
		Instant nextAttemptAt, Instant completedAt, Instant at);

	Optional<PushDispatchGroupContext> findGroupContext(long groupId, int generation, Instant at);

	boolean cancelGroup(long groupId, int generation, Instant at);

	PushBudgetReservation reserveBudget(
		long groupId, int generation, LocalDate budgetDate,
		NotificationType type, PushPolicyProperties properties, Instant at);

	boolean cancelMemberDeliveries(
		long groupId, int generation, Collection<Long> deliveryIds, Instant at);

	List<ClaimedPushDeviceDispatch> claimDevices(
		long groupId, int groupGeneration,
		Map<Long, Set<Long>> eligibleDeliveryIdsByDevice,
		Instant now, Instant leaseUntil);

	boolean completeDevice(
		long groupId, int groupGeneration, long deviceId,
		Map<Long, Integer> deliveryGenerations,
		PushDeliveryTerminalResult result, Instant at,
		Instant nextAttemptAt, String providerMessageId);

	PushDispatchGroupStatus finalizeGroup(long groupId, int groupGeneration, Instant at);

	boolean invalidateClaimedDevice(
		long groupId, int groupGeneration, long deviceId,
		Map<Long, Integer> deliveryGenerations, Instant at);
}
