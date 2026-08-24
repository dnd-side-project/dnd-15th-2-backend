package com.dnd.qello.notification.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.notification.push.ClaimedPushDelivery;
import com.dnd.qello.notification.push.PushDispatchContext;
import com.dnd.qello.notification.push.PushDeliveryTerminalResult;
import com.dnd.qello.notification.repository.NotificationRepository;

/** DB lease와 generation fence의 애플리케이션 경계. provider 호출은 이 경계 밖에서 수행한다. */
@Service
public class PushDeliveryClaimService {

	private final NotificationRepository notificationRepository;

	public PushDeliveryClaimService(NotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	@Transactional
	public List<ClaimedPushDelivery> claimDueDeliveries(int batchSize, Instant now, Instant leaseUntil) {
		return notificationRepository.claimDueDeliveries(batchSize, now, leaseUntil);
	}

	@Transactional
	public boolean completeClaim(
		long deliveryId, int generation, PushDeliveryTerminalResult result, Instant at) {
		return notificationRepository.completeClaim(deliveryId, generation, result, at);
	}

	@Transactional(readOnly = true)
	public Optional<PushDispatchContext> findPushDispatchContext(long deliveryId, int generation, Instant now) {
		return notificationRepository.findPushDispatchContext(deliveryId, generation, now);
	}

	@Transactional
	public boolean completeClaim(
		long deliveryId, int generation, PushDeliveryTerminalResult result, Instant at, Instant nextAttemptAt) {
		return notificationRepository.completeClaim(deliveryId, generation, result, at, nextAttemptAt);
	}

	@Transactional
	public boolean completeClaim(
		long deliveryId,
		int generation,
		PushDeliveryTerminalResult result,
		Instant at,
		Instant nextAttemptAt,
		String providerMessageId
	) {
		return notificationRepository.completeClaim(
			deliveryId, generation, result, at, nextAttemptAt, providerMessageId);
	}

	@Transactional
	public boolean invalidatePushDeviceAndCancelUndelivered(
		long deliveryId, long pushDeviceId, int generation, Instant at) {
		return notificationRepository.invalidatePushDeviceAndCancelUndelivered(
			deliveryId, pushDeviceId, generation, at);
	}
}
