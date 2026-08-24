/**
 * Created at: 2026-08-24T20:30:00+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-006, TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-007,
 * TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-008
 */
package com.dnd.qello.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dnd.qello.notification.domain.PushDevice;
import com.dnd.qello.notification.domain.PushDeviceStatus;
import com.dnd.qello.notification.domain.PushPlatform;
import com.dnd.qello.notification.push.security.ProtectedPushToken;
import com.dnd.qello.notification.push.security.PushToken;
import com.dnd.qello.notification.push.security.PushTokenProtector;
import com.dnd.qello.notification.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class PushDeviceServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-24T11:30:00Z");
	private static final long OWNER_ID = 1791L;
	private static final long OTHER_OWNER_ID = 1792L;
	private static final String TOKEN_SENTINEL = "gh179-unit-token-sentinel";
	private static final String OTHER_TOKEN_SENTINEL = "gh179-unit-token-sentinel-other";
	private static final String REGISTER_ENVELOPE_REASON = "unit-register";
	private static final String REVOKE_ENVELOPE_REASON = "unit-revoke";
	private static final String SERVICE_CLASS = "com.dnd.qello.notification.service.PushDeviceService";
	private static final String COMMAND_CLASS = "com.dnd.qello.notification.service.PushDeviceCommand";

	@Mock
	private PushTokenProtector tokenProtector;

	@Test
	@DisplayName("UNIT-006: 같은 사용자의 재등록은 보호된 ciphertext와 lastSeenAt만 갱신하고 token 원문은 넘기지 않는다")
	void reRegistersSameUserWithFreshCiphertextAndLastSeenAt() throws Exception {
		RepositoryProbe repository = new RepositoryProbe();
		ProtectedPushToken protectedToken = protectedToken(REGISTER_ENVELOPE_REASON);
		PushDevice stored = new PushDevice(101L, OWNER_ID, PushPlatform.ANDROID, protectedToken.envelope(),
			protectedToken.fingerprint(), PushDeviceStatus.ACTIVE, NOW, null);
		repository.registerResults.add(stored);
		when(tokenProtector.protect(PushToken.of(TOKEN_SENTINEL))).thenReturn(protectedToken);
		Object service = newService(repository.repository);

		Object result = invoke(service, "registerOrTransferDevice", OWNER_ID, command(PushPlatform.ANDROID, TOKEN_SENTINEL));

		assertThat(result).isInstanceOf(PushDevice.class);
		assertThat(result).isEqualTo(stored);
		verify(tokenProtector).protect(PushToken.of(TOKEN_SENTINEL));
		verify(tokenProtector, never()).fingerprint(any(PushToken.class));
		assertThat(repository.calls).extracting(Invocation::name)
			.containsExactly("registerOrTransferDevice");
		Invocation call = repository.calls.get(0);
		assertThat(call.args()).hasSize(5);
		assertThat(call.args()[0]).isEqualTo(OWNER_ID);
		assertThat(call.args()[1]).isEqualTo("ANDROID");
		assertThat(call.args()[2]).isInstanceOf(byte[].class);
		assertThat((byte[]) call.args()[2]).isEqualTo(protectedToken.envelope());
		assertThat(call.args()[3]).isEqualTo(protectedToken.fingerprint());
		assertThat(call.args()[4]).isEqualTo(NOW);
		assertThat(call.args()[2].toString()).doesNotContain(TOKEN_SENTINEL);
	}

	@Test
	@DisplayName("UNIT-007: 다른 사용자가 같은 token을 등록하면 새 owner를 반영한 행을 돌려준다")
	void transfersSameTokenToAnotherUserWithoutRevealingPlaintext() throws Exception {
		RepositoryProbe repository = new RepositoryProbe();
		ProtectedPushToken firstProtected = protectedToken(REGISTER_ENVELOPE_REASON);
		ProtectedPushToken secondProtected = protectedToken(REVOKE_ENVELOPE_REASON);
		PushDevice firstStored = new PushDevice(201L, OWNER_ID, PushPlatform.IOS, firstProtected.envelope(),
			firstProtected.fingerprint(), PushDeviceStatus.ACTIVE, NOW, null);
		PushDevice transferredStored = new PushDevice(202L, OTHER_OWNER_ID, PushPlatform.IOS,
			secondProtected.envelope(), secondProtected.fingerprint(), PushDeviceStatus.ACTIVE, NOW, null);
		repository.registerResults.add(firstStored);
		repository.registerResults.add(transferredStored);
		when(tokenProtector.protect(PushToken.of(TOKEN_SENTINEL))).thenReturn(firstProtected, secondProtected);
		Object service = newService(repository.repository);
		Object command = command(PushPlatform.IOS, TOKEN_SENTINEL);

		PushDevice firstResult = (PushDevice) invoke(service, "registerOrTransferDevice", OWNER_ID, command);
		PushDevice secondResult = (PushDevice) invoke(service, "registerOrTransferDevice", OTHER_OWNER_ID, command);

		assertThat(firstResult.userId()).isEqualTo(OWNER_ID);
		assertThat(secondResult.userId()).isEqualTo(OTHER_OWNER_ID);
		assertThat(firstResult.status()).isEqualTo(PushDeviceStatus.ACTIVE);
		assertThat(secondResult.status()).isEqualTo(PushDeviceStatus.ACTIVE);
		assertThat(repository.calls).extracting(Invocation::name)
			.containsExactly("registerOrTransferDevice", "registerOrTransferDevice");
		assertThat(repository.calls.get(0).args()[0]).isEqualTo(OWNER_ID);
		assertThat(repository.calls.get(1).args()[0]).isEqualTo(OTHER_OWNER_ID);
		assertThat(repository.calls.get(0).args()[3]).isEqualTo(firstProtected.fingerprint());
		assertThat(repository.calls.get(1).args()[3]).isEqualTo(secondProtected.fingerprint());
		assertThat(repository.calls.get(0).args()[2]).isInstanceOf(byte[].class);
		assertThat(repository.calls.get(1).args()[2]).isInstanceOf(byte[].class);
		assertThat(Arrays.toString((byte[]) repository.calls.get(0).args()[2])).doesNotContain(TOKEN_SENTINEL);
		assertThat(Arrays.toString((byte[]) repository.calls.get(1).args()[2])).doesNotContain(TOKEN_SENTINEL);
	}

	@Test
	@DisplayName("UNIT-008: revoke는 본인·타인·없는·이미 해지된 token 모두 fingerprint만 쓰고 멱등하게 끝난다")
	void revokesOwnedOtherMissingAndRevokedTokensIdempotently() throws Exception {
		RepositoryProbe repository = new RepositoryProbe();
		repository.revokeResults.addAll(List.of(1, 0, 0, 0));
		when(tokenProtector.fingerprint(PushToken.of(TOKEN_SENTINEL))).thenReturn("fingerprint-owned");
		Object service = newService(repository.repository);
		Object command = command(PushPlatform.ANDROID, TOKEN_SENTINEL);

		int own = (Integer) invoke(service, "revokeOwnedDevice", OWNER_ID, command);
		int other = (Integer) invoke(service, "revokeOwnedDevice", OTHER_OWNER_ID, command);
		int missing = (Integer) invoke(service, "revokeOwnedDevice", OWNER_ID, command);
		int revoked = (Integer) invoke(service, "revokeOwnedDevice", OWNER_ID, command);

		assertThat(own).isEqualTo(1);
		assertThat(other).isZero();
		assertThat(missing).isZero();
		assertThat(revoked).isZero();
		verify(tokenProtector, times(4)).fingerprint(PushToken.of(TOKEN_SENTINEL));
		verify(tokenProtector, never()).protect(any(PushToken.class));
		assertThat(repository.calls).extracting(Invocation::name)
			.containsExactly("revokeOwnedDevice", "revokeOwnedDevice", "revokeOwnedDevice", "revokeOwnedDevice");
		assertThat(repository.calls.get(0).args()[0]).isEqualTo(OWNER_ID);
		assertThat(repository.calls.get(1).args()[0]).isEqualTo(OTHER_OWNER_ID);
		assertThat(repository.calls.get(0).args()[1]).isEqualTo("ANDROID");
		assertThat(repository.calls.get(0).args()[2]).isEqualTo("fingerprint-owned");
		assertThat(repository.calls.get(0).args()[3]).isEqualTo(NOW);
		assertThat(repository.calls.get(1).args()[2]).isEqualTo("fingerprint-owned");
		assertThat(repository.calls.get(2).args()[2]).isEqualTo("fingerprint-owned");
		assertThat(repository.calls.get(3).args()[2]).isEqualTo("fingerprint-owned");
	}

	private Object newService(NotificationRepository repository) {
		try {
			Class<?> serviceType = Class.forName(SERVICE_CLASS);
			for (Constructor<?> constructor : serviceType.getDeclaredConstructors()) {
				Class<?>[] parameterTypes = constructor.getParameterTypes();
				if (parameterTypes.length != 3) {
					continue;
				}
				int repositoryIndex = indexOfAssignable(parameterTypes, NotificationRepository.class);
				int protectorIndex = indexOfAssignable(parameterTypes, PushTokenProtector.class);
				int clockIndex = indexOfAssignable(parameterTypes, Clock.class);
				if (repositoryIndex < 0 || protectorIndex < 0 || clockIndex < 0) {
					continue;
				}
				Object[] arguments = new Object[3];
				arguments[repositoryIndex] = repository;
				arguments[protectorIndex] = tokenProtector;
				arguments[clockIndex] = Clock.fixed(NOW, ZoneOffset.UTC);
				constructor.setAccessible(true);
				return constructor.newInstance(arguments);
			}
			throw new AssertionError("PushDeviceService constructor with repository, protector, and clock is required");
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError("PushDeviceService is not available yet", exception);
		}
	}

	private Object command(PushPlatform platform, String tokenValue) {
		try {
			Class<?> commandType = Class.forName(COMMAND_CLASS);
			for (Constructor<?> constructor : commandType.getDeclaredConstructors()) {
				Class<?>[] parameterTypes = constructor.getParameterTypes();
				if (parameterTypes.length != 2) {
					continue;
				}
				Object first = convertParameter(parameterTypes[0], constructor.getParameters()[0].getName(), 0, platform,
					tokenValue);
				Object second = convertParameter(parameterTypes[1], constructor.getParameters()[1].getName(), 1, platform,
					tokenValue);
				if (first == UNMATCHED || second == UNMATCHED) {
					continue;
				}
				constructor.setAccessible(true);
				return constructor.newInstance(first, second);
			}
			throw new AssertionError("PushDeviceCommand constructor accepting platform and token is required");
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError("PushDeviceCommand is not available yet", exception);
		}
	}

	private Object invoke(Object service, String methodName, long userId, Object command) {
		try {
			Method method = service.getClass().getMethod(methodName, long.class, command.getClass());
			method.setAccessible(true);
			return method.invoke(service, userId, command);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError("PushDeviceService method " + methodName + " is not available yet", exception);
		}
	}

	private ProtectedPushToken protectedToken(String label) {
		byte[] envelope = (label + "-envelope").getBytes();
		return new ProtectedPushToken(envelope, label + "-fingerprint");
	}

	private int indexOfAssignable(Class<?>[] parameterTypes, Class<?> expectedType) {
		for (int index = 0; index < parameterTypes.length; index++) {
			if (expectedType.isAssignableFrom(parameterTypes[index])) {
				return index;
			}
		}
		return -1;
	}

	private Object convertParameter(Class<?> parameterType, String parameterName, int index, PushPlatform platform,
		String tokenValue) {
		if (parameterType.isEnum() && parameterType.isAssignableFrom(platform.getDeclaringClass())) {
			return platform;
		}
		if (parameterType.equals(String.class)) {
			String lowerName = parameterName == null ? "" : parameterName.toLowerCase();
			if (lowerName.contains("platform")) {
				return platform.name();
			}
			if (lowerName.contains("token")) {
				return tokenValue;
			}
			return index == 0 ? platform.name() : tokenValue;
		}
		if (parameterType.getName().equals(PushToken.class.getName())) {
			return PushToken.of(tokenValue);
		}
		if (parameterType.isAssignableFrom(PushPlatform.class)) {
			return platform;
		}
		return UNMATCHED;
	}

	private static final Object UNMATCHED = new Object();

	private record Invocation(String name, Object[] args) {
		Invocation {
			args = args == null ? new Object[0] : args.clone();
		}

		@Override
		public Object[] args() {
			return args.clone();
		}
	}

	private final class RepositoryProbe implements InvocationHandler {

		private final Deque<PushDevice> registerResults = new ArrayDeque<>();
		private final Deque<Integer> revokeResults = new ArrayDeque<>();
		private final List<Invocation> calls = new ArrayList<>();
		private final NotificationRepository repository = (NotificationRepository) Proxy.newProxyInstance(
			NotificationRepository.class.getClassLoader(), new Class<?>[] {NotificationRepository.class}, this);

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			Object[] safeArgs = args == null ? new Object[0] : args.clone();
			calls.add(new Invocation(method.getName(), safeArgs));
			return switch (method.getName()) {
				case "registerOrTransferDevice" -> poll(registerResults, "registerOrTransferDevice");
				case "revokeOwnedDevice" -> poll(revokeResults, "revokeOwnedDevice");
				default -> defaultValue(method.getReturnType());
			};
		}

		private Object poll(Deque<?> values, String methodName) {
			if (values.isEmpty()) {
				throw new AssertionError("unexpected call to " + methodName);
			}
			return values.removeFirst();
		}

		private Object defaultValue(Class<?> returnType) {
			if (returnType.equals(void.class)) {
				return null;
			}
			if (returnType.equals(boolean.class)) {
				return false;
			}
			if (returnType.equals(byte.class) || returnType.equals(short.class)
				|| returnType.equals(int.class) || returnType.equals(long.class)
				|| returnType.equals(char.class)) {
				return 0;
			}
			if (returnType.equals(float.class) || returnType.equals(double.class)) {
				return 0.0;
			}
			if (returnType.isArray()) {
				return Array.newInstance(returnType.componentType(), 0);
			}
			return null;
		}
	}
}
