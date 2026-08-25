package com.dnd.qello.notification.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.notification.config.PushPolicyProperties;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.push.PushDeliveryTerminalResult;
import com.dnd.qello.notification.push.group.ClaimedPushDeviceDispatch;
import com.dnd.qello.notification.push.group.ClaimedPushDispatchGroup;
import com.dnd.qello.notification.push.group.PushBudgetReservation;
import com.dnd.qello.notification.push.group.PushDispatchGroupContext;
import com.dnd.qello.notification.push.group.PushDispatchGroupStatus;
import com.dnd.qello.notification.repository.PushDispatchGroupRepository;

/** group lease와 generation fence의 애플리케이션 경계. provider 호출은 이 경계 밖에서 수행한다. */
@Service
public class PushDispatchGroupClaimService {

	private final PushDispatchGroupRepository repository;

	public PushDispatchGroupClaimService(PushDispatchGroupRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public List<ClaimedPushDispatchGroup> claimDueGroups(int limit, Instant now, Instant leaseUntil) {
		return repository.claimDueGroups(limit, now, leaseUntil);
	}

	@Transactional
	public boolean transition(
		long groupId, int generation, PushDispatchGroupStatus status,
		Instant nextAttemptAt, Instant completedAt, Instant at) {
		return repository.transition(groupId, generation, status, nextAttemptAt, completedAt, at);
	}

	@Transactional(readOnly = true)
	public Optional<PushDispatchGroupContext> loadContext(long groupId, int generation, Instant at) {
		return repository.findGroupContext(groupId, generation, at);
	}

	@Transactional
	public boolean deferGroup(long groupId, int generation, Instant nextAt, Instant at) {
		return repository.transition(
			groupId, generation, PushDispatchGroupStatus.PENDING, nextAt, null, at);
	}

	@Transactional
	public boolean cancelGroup(long groupId, int generation, Instant at) {
		return repository.cancelGroup(groupId, generation, at);
	}

	@Transactional
	public PushBudgetReservation reserveBudget(
		long groupId, int generation, LocalDate budgetDate,
		NotificationType type, PushPolicyProperties properties, Instant at) {
		return repository.reserveBudget(groupId, generation, budgetDate, type, properties, at);
	}

	@Transactional
	public boolean cancelMemberDeliveries(
		long groupId, int generation, Collection<Long> deliveryIds, Instant at) {
		return repository.cancelMemberDeliveries(groupId, generation, deliveryIds, at);
	}

	@Transactional
	public List<ClaimedPushDeviceDispatch> claimDevices(
		long groupId, int groupGeneration,
		Map<Long, Set<Long>> eligibleDeliveryIdsByDevice,
		Instant now, Instant leaseUntil) {
		return repository.claimDevices(
			groupId, groupGeneration, eligibleDeliveryIdsByDevice, now, leaseUntil);
	}

	@Transactional
	public boolean completeDevice(
		long groupId, int groupGeneration, long deviceId,
		Map<Long, Integer> deliveryGenerations,
		PushDeliveryTerminalResult result, Instant at,
		Instant nextAttemptAt, String providerMessageId) {
		return repository.completeDevice(
			groupId, groupGeneration, deviceId, deliveryGenerations, result, at, nextAttemptAt, providerMessageId);
	}

	@Transactional
	public PushDispatchGroupStatus finalizeGroup(long groupId, int groupGeneration, Instant at) {
		return repository.finalizeGroup(groupId, groupGeneration, at);
	}

	@Transactional
	public boolean invalidateClaimedDevice(
		long groupId, int groupGeneration, long deviceId,
		Map<Long, Integer> deliveryGenerations, Instant at) {
		return repository.invalidateClaimedDevice(
			groupId, groupGeneration, deviceId, deliveryGenerations, at);
	}
}
