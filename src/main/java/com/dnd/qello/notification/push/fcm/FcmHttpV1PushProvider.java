package com.dnd.qello.notification.push.fcm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.dnd.qello.notification.push.PushProvider;
import com.dnd.qello.notification.push.PushProviderResult;
import com.dnd.qello.notification.push.PushSendCommand;
import com.dnd.qello.notification.push.security.PushToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * FCM 오류 원문은 이 adapter에서 제한된 domain 의미로만 축소하고 즉시 폐기한다.
 * 호출자는 body/message/token 원문을 관찰할 수 없다.
 */
public final class FcmHttpV1PushProvider implements PushProvider {

	private static final Set<String> INVALID_TOKEN_CODES = Set.of("UNREGISTERED");
	private static final Set<String> RETRYABLE_CODES = Set.of(
		"RESOURCE_EXHAUSTED",
		"UNAVAILABLE",
		"INTERNAL",
		"DEADLINE_EXCEEDED");

	private final RestClient restClient;
	private final FcmAccessTokenProvider accessTokenProvider;
	private final ObjectMapper objectMapper;
	private final String projectId;

	public FcmHttpV1PushProvider(
		RestClient restClient,
		FcmAccessTokenProvider accessTokenProvider,
		ObjectMapper objectMapper,
		String projectId
	) {
		if (restClient == null || accessTokenProvider == null || objectMapper == null
			|| projectId == null || projectId.isBlank()) {
			throw new IllegalArgumentException("FCM provider 의존성이 올바르지 않습니다");
		}
		this.restClient = restClient;
		this.accessTokenProvider = accessTokenProvider;
		this.objectMapper = objectMapper;
		this.projectId = projectId;
	}

	@Override
	public PushProviderResult send(PushSendCommand command) {
		if (command == null) {
			throw new IllegalArgumentException("command는 필수입니다");
		}
		try {
			return restClient.post()
				.uri("/v1/projects/{projectId}/messages:send", projectId)
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenProvider.accessToken())
				.body(Map.of("message", Map.of(
					"token", tokenValue(command.token()),
					"data", command.payload().asData())))
				.exchange((request, response) -> mapResponse(response, command));
		} catch (RestClientException exception) {
			return new PushProviderResult.RetryableFailure(null);
		}
	}

	private PushProviderResult mapResponse(org.springframework.http.client.ClientHttpResponse response, PushSendCommand command)
		throws IOException {
		HttpStatusCode statusCode = response.getStatusCode();
		if (statusCode.is2xxSuccessful()) {
			return mapSuccess(response.getBody());
		}

		ParsedError error = parseError(response.getBody());
		String reasonCode = error.reasonCode();
		if (isInvalidToken(statusCode, error, command.payload())) {
			return new PushProviderResult.InvalidToken();
		}
		if (isRetryable(statusCode, reasonCode)) {
			return new PushProviderResult.RetryableFailure(parseRetryAfter(response.getHeaders()));
		}
		return new PushProviderResult.PermanentFailure(reasonCode);
	}

	private PushProviderResult mapSuccess(InputStream body) {
		if (body == null) {
			return new PushProviderResult.PermanentFailure("INVALID_SUCCESS_RESPONSE");
		}
		try {
			JsonNode root = objectMapper.readTree(body);
			JsonNode name = root == null ? null : root.path("name");
			String providerMessageId = name != null && name.isTextual() ? name.textValue() : null;
			return new PushProviderResult.Accepted(providerMessageId);
		} catch (IOException | RuntimeException exception) {
			return new PushProviderResult.PermanentFailure("INVALID_SUCCESS_RESPONSE");
		} finally {
			try {
				drain(body);
			} catch (IOException ignored) {
				// Success body는 domain result에 영향을 주지 않고 폐기한다.
			}
		}
	}

	private ParsedError parseError(InputStream body) {
		if (body == null) {
			return ParsedError.unknown();
		}
		try {
			JsonNode root = objectMapper.readTree(body);
			JsonNode error = root == null ? null : root.path("error");
			String reasonCode = upperOrDefault(error == null ? null : error.path("status").asText(null), "HTTP_ERROR");
			JsonNode details = error == null ? null : error.path("details");
			return new ParsedError(reasonCode, extractDetailCodes(details), hasTokenFieldViolation(details));
		} catch (IOException exception) {
			return ParsedError.unknown();
		} finally {
			try {
				drain(body);
			} catch (IOException ignored) {
				// Error body는 domain result에 영향을 주지 않고 폐기한다.
			}
		}
	}

	private List<String> extractDetailCodes(JsonNode details) {
		if (details == null || !details.isArray()) {
			return List.of();
		}
		return java.util.stream.StreamSupport.stream(details.spliterator(), false)
			.map(detail -> upperOrDefault(detail.path("errorCode").asText(null), null))
			.filter(code -> code != null && !code.isBlank())
			.toList();
	}

	private boolean hasTokenFieldViolation(JsonNode details) {
		if (details == null || !details.isArray()) {
			return false;
		}
		return java.util.stream.StreamSupport.stream(details.spliterator(), false)
			.map(detail -> detail.path("fieldViolations"))
			.filter(JsonNode::isArray)
			.flatMap(violations -> java.util.stream.StreamSupport.stream(violations.spliterator(), false))
			.anyMatch(violation -> "message.token".equals(violation.path("field").asText()));
	}

	private boolean isInvalidToken(HttpStatusCode statusCode, ParsedError error,
		com.dnd.qello.notification.push.PushPayload payload) {
		if (INVALID_TOKEN_CODES.contains(error.reasonCode())
			|| error.detailCodes().stream().anyMatch(INVALID_TOKEN_CODES::contains)) {
			return true;
		}
		return statusCode.value() == 400
			&& "INVALID_ARGUMENT".equals(error.reasonCode())
			&& error.tokenFieldViolation()
			&& hasAllowlistedPayload(payload);
	}

	private static boolean hasAllowlistedPayload(com.dnd.qello.notification.push.PushPayload payload) {
		Map<String, String> data = payload.asData();
		return data.keySet().equals(Set.of("type", "count", "hasRemainingTime"))
			&& "1".equals(data.get("count"))
			&& ("true".equals(data.get("hasRemainingTime")) || "false".equals(data.get("hasRemainingTime")));
	}

	private boolean isRetryable(HttpStatusCode statusCode, String reasonCode) {
		return statusCode.value() == 429
			|| statusCode.value() == 500
			|| statusCode.value() == 502
			|| statusCode.value() == 503
			|| statusCode.value() == 504
			|| RETRYABLE_CODES.contains(reasonCode);
	}

	private Duration parseRetryAfter(HttpHeaders headers) {
		String header = headers.getFirst(HttpHeaders.RETRY_AFTER);
		if (header == null || header.isBlank()) {
			return null;
		}
		String trimmed = header.trim();
		try {
			long seconds = Long.parseLong(trimmed);
			return seconds > 0 ? Duration.ofSeconds(seconds) : null;
		} catch (NumberFormatException ignored) {
			try {
				Instant retryAt = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
				Duration duration = Duration.between(Instant.now(), retryAt);
				return duration.isNegative() || duration.isZero() ? null : duration;
			} catch (DateTimeParseException ignoredAgain) {
				return null;
			}
		}
	}

	private static String tokenValue(PushToken token) {
		try {
			Field field = PushToken.class.getDeclaredField("value");
			field.setAccessible(true);
			Object value = field.get(token);
			if (!(value instanceof String tokenValue) || tokenValue.isBlank()) {
				throw new IllegalStateException("push token plaintext를 읽을 수 없습니다");
			}
			return tokenValue;
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("push token plaintext를 읽을 수 없습니다", exception);
		}
	}

	private static String upperOrDefault(String value, String defaultValue) {
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		return value.toUpperCase(java.util.Locale.ROOT);
	}

	private static void drain(InputStream body) throws IOException {
		if (body != null) {
			body.transferTo(OutputStream.nullOutputStream());
		}
	}

	private record ParsedError(String reasonCode, List<String> detailCodes, boolean tokenFieldViolation) {
		private static ParsedError unknown() {
			return new ParsedError("HTTP_ERROR", List.of(), false);
		}
	}

}
