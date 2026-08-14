package com.dnd.qello.direction.domain;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.List;

/**
 * 발송 요청의 사용자 의도를 식별하는 v1 fingerprint다.
 */
public record DirectionRequestFingerprint(String value) {

    private static final String VERSION_PREFIX = "v1:";
    private static final int DIGEST_HEX_LENGTH = 64;

    public DirectionRequestFingerprint {
        if (value == null || !value.startsWith(VERSION_PREFIX)) {
            throw new DirectionException(
                    DirectionErrorCode.INVALID_TEXT, "requestFingerprint", "requestFingerprint 형식이 유효하지 않습니다");
        }
        if (value.length() != VERSION_PREFIX.length() + DIGEST_HEX_LENGTH
                || !value.substring(VERSION_PREFIX.length()).matches("[0-9a-f]{64}")) {
            throw new DirectionException(
                    DirectionErrorCode.INVALID_TEXT, "requestFingerprint", "requestFingerprint 형식이 유효하지 않습니다");
        }
    }

    public static DirectionRequestFingerprint create(Long approvedQuestionId, Long schemeId, String segmentKey,
                                                     long minDistanceMeters, long maxDistanceMeters, String bodyText) {
        return create(approvedQuestionId, schemeId, segmentKey, minDistanceMeters, maxDistanceMeters,
                bodyText, List.of());
    }

    /**
     * coarse region은 서버가 저장하는 표시 snapshot이지 사용자의 의도가 아니므로 fingerprint에서 제외한다.
     */
    public static DirectionRequestFingerprint create(Long approvedQuestionId, Long schemeId, String segmentKey,
                                                     long minDistanceMeters, long maxDistanceMeters, String bodyText,
                                                     List<Long> mediaIds) {
        validateId(approvedQuestionId, "approvedQuestionId");
        validateId(schemeId, "schemeId");
        if (minDistanceMeters < 0 || maxDistanceMeters <= minDistanceMeters) {
            throw new DirectionException(
                    DirectionErrorCode.INVALID_DISTANCE_RANGE, "maxDistanceMeters", "거리 범위가 유효하지 않습니다");
        }
        if (mediaIds == null || mediaIds.size() > 1 || mediaIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new DirectionException(DirectionErrorCode.INVALID_VALUE_RANGE, "mediaIds", "미디어 수 또는 ID가 유효하지 않습니다");
        }

        String canonicalJson = "{" +
                jsonNumberField("approvedQuestionId", approvedQuestionId.toString()) + "," +
                jsonNumberField("schemeId", schemeId.toString()) + "," +
                jsonStringField("segmentKey", segmentKey, false) + "," +
                jsonNumberField("minDistanceMeters", Long.toString(minDistanceMeters)) + "," +
                jsonNumberField("maxDistanceMeters", Long.toString(maxDistanceMeters)) + "," +
                jsonStringField("bodyText", bodyText, true) + "," +
                jsonMediaIdsField(mediaIds) +
                "}";

        return digest(canonicalJson);
    }

    /**
     * 기존 행의 nullable 저장값을 도메인 값으로 복원한다.
     */
    public static DirectionRequestFingerprint restore(String storedValue) {
        return storedValue == null ? null : new DirectionRequestFingerprint(storedValue);
    }

    private static DirectionRequestFingerprint digest(String canonicalJson) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
            return new DirectionRequestFingerprint(VERSION_PREFIX + hex);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    private static String jsonNumberField(String name, String value) {
        return "\"" + name + "\":" + value;
    }

    private static String jsonStringField(String name, String value, boolean nullable) {
        if (value == null && nullable) return "\"" + name + "\":null";
        if (value == null) {
            throw new DirectionException(DirectionErrorCode.REQUIRED_VALUE_MISSING, name, name + "는 필수입니다");
        }
        String normalized = nullable ? normalizeBodyText(value) : normalize(value);
        if (normalized == null || normalized.isBlank()) {
            if (nullable) return "\"" + name + "\":null";
            throw new DirectionException(DirectionErrorCode.INVALID_TEXT, name, name + "이 유효하지 않습니다");
        }
        return "\"" + name + "\":\"" + escapeJson(normalized) + "\"";
    }

    private static String jsonMediaIdsField(List<Long> mediaIds) {
        StringBuilder json = new StringBuilder("\"mediaIds\":[");
        for (int index = 0; index < mediaIds.size(); index++) {
            if (index > 0) json.append(',');
            json.append(mediaIds.get(index));
        }
        return json.append(']').toString();
    }

    private static String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        int start = 0;
        int end = normalized.length();
        while (start < end) {
            int codePoint = normalized.codePointAt(start);
            if (!isUnicodeWhitespace(codePoint)) break;
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = normalized.codePointBefore(end);
            if (!isUnicodeWhitespace(codePoint)) break;
            end -= Character.charCount(codePoint);
        }
        return normalized.substring(start, end);
    }

    /** 본문은 NFC와 바깥 Unicode 공백을 정규화하고, 공백-only 입력을 null로 통일한다. */
    public static String normalizeBodyText(String value) {
        if (value == null) return null;
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private static boolean isUnicodeWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) escaped.append(String.format("\\u%04x", (int) character));
                    else escaped.append(character);
                }
            }
        }
        return escaped.toString();
    }

    private static void validateId(Long value, String field) {
        if (value == null || value <= 0) {
            throw new DirectionException(DirectionErrorCode.INVALID_ID, field, field + "는 양수여야 합니다");
        }
    }
}
