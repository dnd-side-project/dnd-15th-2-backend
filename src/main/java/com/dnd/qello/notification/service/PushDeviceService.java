package com.dnd.qello.notification.service;

import java.time.Clock;
import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.notification.domain.PushDevice;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.push.security.ProtectedPushToken;
import com.dnd.qello.notification.push.security.PushTokenProtectionException;
import com.dnd.qello.notification.push.security.PushTokenProtector;
import com.dnd.qello.notification.repository.NotificationRepository;

@Service
public class PushDeviceService {

	private final NotificationRepository notificationRepository;
	private final PushTokenProtector tokenProtector;
	private final Clock clock;

	@Autowired
	PushDeviceService(
		NotificationRepository notificationRepository,
		ObjectProvider<PushTokenProtector> tokenProtectorProvider,
		Clock clock) {
		this(
			notificationRepository,
			tokenProtectorProvider.getIfAvailable(UnconfiguredPushTokenProtector::new),
			clock);
	}

	public PushDeviceService(
		NotificationRepository notificationRepository,
		PushTokenProtector tokenProtector,
		Clock clock) {
		this.notificationRepository = Objects.requireNonNull(notificationRepository, "notificationRepository");
		this.tokenProtector = Objects.requireNonNull(tokenProtector, "tokenProtector");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Transactional
	public PushDevice registerOrTransferDevice(long userId, PushDeviceCommand command) {
		requireUserId(userId);
		PushDeviceCommand safeCommand = requireCommand(command);
		try {
			ProtectedPushToken protectedToken = tokenProtector.protect(safeCommand.token());
			return notificationRepository.registerOrTransferDevice(
				userId,
				safeCommand.platform().name(),
				protectedToken.envelope(),
				protectedToken.fingerprint(),
				clock.instant());
		}
		catch (PushTokenProtectionException exception) {
			throw new NotificationException(
				NotificationErrorCode.PUSH_TOKEN_PROTECTION_FAILED,
				"token",
				"push token을 안전하게 처리할 수 없습니다.",
				exception);
		}
	}

	@Transactional
	public int revokeOwnedDevice(long userId, PushDeviceCommand command) {
		requireUserId(userId);
		PushDeviceCommand safeCommand = requireCommand(command);
		try {
			return notificationRepository.revokeOwnedDevice(
				userId,
				safeCommand.platform().name(),
				tokenProtector.fingerprint(safeCommand.token()),
				clock.instant());
		}
		catch (PushTokenProtectionException exception) {
			throw new NotificationException(
				NotificationErrorCode.PUSH_TOKEN_PROTECTION_FAILED,
				"token",
				"push token을 안전하게 처리할 수 없습니다.",
				exception);
		}
	}

	private void requireUserId(long userId) {
		if (userId <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_ID, "userId", "userId는 양수여야 합니다.");
		}
	}

	private PushDeviceCommand requireCommand(PushDeviceCommand command) {
		if (command == null) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_PUSH_DEVICE_REQUEST, "request", "push device 요청 본문은 필수입니다.");
		}
		return command;
	}

	private static final class UnconfiguredPushTokenProtector implements PushTokenProtector {

		@Override
		public ProtectedPushToken protect(com.dnd.qello.notification.push.security.PushToken token) {
			throw new PushTokenProtectionException();
		}

		@Override
		public com.dnd.qello.notification.push.security.PushToken decrypt(byte[] envelope) {
			throw new PushTokenProtectionException();
		}

		@Override
		public String fingerprint(com.dnd.qello.notification.push.security.PushToken token) {
			throw new PushTokenProtectionException();
		}
	}
}
