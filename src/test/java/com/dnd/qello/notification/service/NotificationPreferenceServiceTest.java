/**
 * Created at: 2026-08-21T18:55:00+09:00
 * Source scenario: TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-007 through
 * TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-013
 */
package com.dnd.qello.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.service.AccountEligibilityGate;
import com.dnd.qello.notification.domain.NotificationPreferenceSnapshot;
import com.dnd.qello.notification.domain.NotificationQuietHours;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.repository.NotificationPreferenceRepository;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

	private static final long USER_ID = 37L;

	@Mock private AccountEligibilityGate accountEligibilityGate;
	@Mock private NotificationPreferenceRepository preferences;

	private NotificationPreferenceService service;

	@BeforeEach
	void setUp() {
		service = new NotificationPreferenceService(accountEligibilityGate, preferences);
	}

	@Test
	@DisplayName("UNIT-007 완성된 6종 command는 생성할 수 있다")
	void acceptsCompleteTypeSetCommand() {
		UpdateNotificationPreferences command = command(true, mixedTypes(), overnightQuietHours());

		assertThat(command.pushEnabled()).isTrue();
		assertThat(command.typeEnabled()).isEqualTo(mixedTypes());
		assertThat(command.quietHours()).isEqualTo(overnightQuietHours());
	}

	@Test
	@DisplayName("UNIT-008 type 설정이 null이면 command 생성 시점에 NOT-VAL-008이다")
	void rejectsNullTypeSetAtCommandBoundary() {
		assertThatThrownBy(() -> new UpdateNotificationPreferences(true, null, null))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_PREFERENCE);
	}

	@Test
	@DisplayName("UNIT-008 PUT command에 알림 종류가 누락되면 저장을 시작하지 않는다")
	void rejectsIncompleteTypeSetBeforeWrite() {
		UpdateNotificationPreferences command = command(true, without(NotificationType.REPORT_RESOLVED), null);

		assertThatThrownBy(() -> service.replaceMine(USER_ID, command))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_PREFERENCE);
		verifyNoInteractions(preferences);
	}

	@Test
	@DisplayName("UNIT-009 설정 행이 없더라도 GET은 repository가 준 canonical snapshot을 그대로 반환한다")
	void findMineReturnsCanonicalDefaultSnapshot() {
		stubEligibleAccount();
		NotificationPreferenceSnapshot snapshot = new NotificationPreferenceSnapshot(USER_ID, true, null, allEnabled());
		when(preferences.findByUserId(USER_ID)).thenReturn(snapshot);

		NotificationPreferenceSnapshot result = service.findMine(USER_ID);

		assertThat(result).isSameAs(snapshot);
		verify(preferences).findByUserId(USER_ID);
	}

	@Test
	@DisplayName("UNIT-010 전체 OFF 저장은 종류별 혼합값을 덮어쓰지 않는다")
	void preservesTypeChoicesWhenMasterIsOff() {
		stubEligibleAccount();
		Map<NotificationType, Boolean> mixedTypes = mixedTypes();
		UpdateNotificationPreferences command = command(false, mixedTypes, null);
		NotificationPreferenceSnapshot saved = new NotificationPreferenceSnapshot(USER_ID, false, null, mixedTypes);
		when(preferences.findByUserId(USER_ID)).thenReturn(saved);

		NotificationPreferenceSnapshot result = service.replaceMine(USER_ID, command);

		assertThat(result).isSameAs(saved);
		InOrder inOrder = inOrder(preferences, accountEligibilityGate);
		inOrder.verify(preferences).lockUser(USER_ID);
		inOrder.verify(accountEligibilityGate).requireActiveUser(eq(USER_ID), any(), any());
		inOrder.verify(preferences).saveUserSetting(USER_ID, false, null);
		inOrder.verify(preferences).replaceTypePreferences(USER_ID, mixedTypes);
		inOrder.verify(preferences).findByUserId(USER_ID);
	}

	@Test
	@DisplayName("UNIT-011 전체 ON 저장도 전달된 종류별 snapshot을 그대로 사용한다")
	void preservesTypeChoicesWhenMasterIsOn() {
		stubEligibleAccount();
		Map<NotificationType, Boolean> mixedTypes = mixedTypes();
		NotificationQuietHours quietHours = overnightQuietHours();
		UpdateNotificationPreferences command = command(true, mixedTypes, quietHours);
		NotificationPreferenceSnapshot saved = new NotificationPreferenceSnapshot(USER_ID, true, quietHours, mixedTypes);
		when(preferences.findByUserId(USER_ID)).thenReturn(saved);

		NotificationPreferenceSnapshot result = service.replaceMine(USER_ID, command);

		assertThat(result).isSameAs(saved);
		verify(preferences).saveUserSetting(USER_ID, true, quietHours);
		verify(preferences).replaceTypePreferences(USER_ID, mixedTypes);
	}

	@Test
	@DisplayName("UNIT-012 존재하지 않는 계정이면 GET과 PUT 모두 NOT-APP-001이고 PUT은 저장하지 않는다")
	void rejectsUnknownAccountAcrossGetAndPut() {
		stubGateToInvoke(1);
		UpdateNotificationPreferences command = command(true, mixedTypes(), null);

		assertThatThrownBy(() -> service.findMine(USER_ID))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.ACCOUNT_NOT_FOUND);
		verifyNoInteractions(preferences);

		doThrow(new NotificationException(NotificationErrorCode.ACCOUNT_NOT_FOUND))
			.when(preferences).lockUser(USER_ID);
		assertThatThrownBy(() -> service.replaceMine(USER_ID, command))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.ACCOUNT_NOT_FOUND);
		verify(preferences).lockUser(USER_ID);
		verify(preferences, never()).saveUserSetting(anyLong(), anyBoolean(), any());
		verify(preferences, never()).replaceTypePreferences(anyLong(), any());
	}

	@Test
	@DisplayName("UNIT-013 USER가 아니거나 비활성 계정이면 GET과 PUT 모두 NOT-APP-002이고 PUT은 저장하지 않는다")
	void rejectsIneligibleAccountAcrossGetAndPut() {
		stubGateToInvoke(2);
		UpdateNotificationPreferences command = command(true, mixedTypes(), null);

		assertThatThrownBy(() -> service.findMine(USER_ID))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.ACCOUNT_NOT_ELIGIBLE);
		assertThatThrownBy(() -> service.replaceMine(USER_ID, command))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.ACCOUNT_NOT_ELIGIBLE);
		verify(preferences).lockUser(USER_ID);
		verify(preferences, never()).saveUserSetting(anyLong(), anyBoolean(), any());
		verify(preferences, never()).replaceTypePreferences(anyLong(), any());
	}

	private void stubEligibleAccount() {
		when(accountEligibilityGate.requireActiveUser(eq(USER_ID), any(), any()))
			.thenReturn(userAccount(AccountStatus.ACTIVE));
	}

	@SuppressWarnings("unchecked")
	private void stubGateToInvoke(int supplierIndex) {
		when(accountEligibilityGate.requireActiveUser(eq(USER_ID), any(), any()))
			.thenAnswer(invocation -> {
				Supplier<? extends RuntimeException> supplier = invocation.getArgument(supplierIndex);
				throw supplier.get();
			});
	}

	private static UpdateNotificationPreferences command(
		boolean pushEnabled,
		Map<NotificationType, Boolean> typeEnabled,
		NotificationQuietHours quietHours) {
		return new UpdateNotificationPreferences(pushEnabled, quietHours, typeEnabled);
	}

	private static Map<NotificationType, Boolean> allEnabled() {
		EnumMap<NotificationType, Boolean> values = new EnumMap<>(NotificationType.class);
		for (NotificationType type : NotificationType.values()) {
			values.put(type, true);
		}
		return values;
	}

	private static Map<NotificationType, Boolean> mixedTypes() {
		EnumMap<NotificationType, Boolean> values = new EnumMap<>(NotificationType.class);
		values.put(NotificationType.ANSWER_RECEIVED, true);
		values.put(NotificationType.ANSWER_REACTED, false);
		values.put(NotificationType.DIRECTION_POST_RECEIVED, true);
		values.put(NotificationType.REPORT_RESOLVED, false);
		values.put(NotificationType.QUESTION_PROPOSAL_REVIEWED, true);
		values.put(NotificationType.QUESTION_RECOMMENDED, false);
		return values;
	}

	private static Map<NotificationType, Boolean> without(NotificationType omittedType) {
		EnumMap<NotificationType, Boolean> values = new EnumMap<>(mixedTypes());
		values.remove(omittedType);
		return values;
	}

	private static NotificationQuietHours overnightQuietHours() {
		return new NotificationQuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneId.of("Asia/Seoul"));
	}

	private static Account userAccount(AccountStatus status) {
		return Account.restore(
			USER_ID, AccountRole.USER, status, "KR", "KR-11", "ko-KR", "Asia/Seoul",
			"notification-preference-user",
			status == AccountStatus.DELETED ? Instant.parse("2026-08-01T00:00:00Z") : null);
	}
}
