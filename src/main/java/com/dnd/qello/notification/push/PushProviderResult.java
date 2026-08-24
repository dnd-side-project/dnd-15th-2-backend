package com.dnd.qello.notification.push;

import java.time.Duration;
import java.util.regex.Pattern;

/** FCM adapter와 worker 사이의 제한된 provider 결과 계약. 원문 response body는 포함하지 않는다. */
public sealed interface PushProviderResult
	permits PushProviderResult.Accepted, PushProviderResult.InvalidToken,
	PushProviderResult.RetryableFailure, PushProviderResult.PermanentFailure {

	record Accepted(String providerMessageId) implements PushProviderResult {
		private static final int MAX_PROVIDER_MESSAGE_ID_LENGTH = 255;

		public Accepted {
			if (providerMessageId == null || providerMessageId.isBlank()
				|| providerMessageId.length() > MAX_PROVIDER_MESSAGE_ID_LENGTH
				|| providerMessageId.chars().anyMatch(value -> Character.isWhitespace(value)
					|| Character.isSpaceChar(value) || Character.isISOControl(value))) {
				throw new IllegalArgumentException("providerMessageId가 올바르지 않습니다");
			}
		}
	}

	record InvalidToken() implements PushProviderResult {
	}

	record RetryableFailure(Duration retryAfter) implements PushProviderResult {
		public RetryableFailure {
			if (retryAfter != null && retryAfter.isNegative()) {
				throw new IllegalArgumentException("retryAfter는 음수일 수 없습니다");
			}
		}
	}

	record PermanentFailure(String safeReasonCode) implements PushProviderResult {
		private static final Pattern SAFE_REASON_CODE = Pattern.compile("[A-Z0-9][A-Z0-9_.:-]{0,63}");

		public PermanentFailure {
			if (safeReasonCode == null || !SAFE_REASON_CODE.matcher(safeReasonCode).matches()) {
				throw new IllegalArgumentException("safeReasonCode가 올바르지 않습니다");
			}
		}
	}
}
