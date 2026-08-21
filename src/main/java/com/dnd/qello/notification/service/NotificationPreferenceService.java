package com.dnd.qello.notification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.service.AccountEligibilityGate;
import com.dnd.qello.notification.domain.NotificationPreferenceSnapshot;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.repository.NotificationPreferenceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

	private final AccountEligibilityGate accountEligibilityGate;
	private final NotificationPreferenceRepository preferences;

	@Transactional(readOnly = true)
	public NotificationPreferenceSnapshot findMine(long userId) {
		requireEligibleAccount(userId);
		return preferences.findByUserId(userId);
	}

	@Transactional
	public NotificationPreferenceSnapshot replaceMine(long userId, UpdateNotificationPreferences command) {
		requireEligibleAccount(userId);
		command.requireCompleteTypeSet();
		preferences.lockUser(userId);
		preferences.saveUserSetting(userId, command.pushEnabled(), command.quietHours());
		preferences.replaceTypePreferences(userId, command.typeEnabled());
		return preferences.findByUserId(userId);
	}

	private void requireEligibleAccount(long accountId) {
		accountEligibilityGate.requireActiveUser(
			accountId,
			() -> new NotificationException(NotificationErrorCode.ACCOUNT_NOT_FOUND),
			() -> new NotificationException(NotificationErrorCode.ACCOUNT_NOT_ELIGIBLE));
	}
}
