package com.dnd.qello.safety.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

// 신고 시점 증거의 내용 동일성 판정에 쓰는 순수 함수. media key를 정렬한 뒤
// 계산해 순서에 무관하다 - #154의 "종결 후 내용 미변경 재신고" 판정이 이 값에
// 의존한다.
public final class ReportContentHasher {

	private static final String SEPARATOR = "|";

	private ReportContentHasher() {
	}

	public static String hash(String bodyText, List<String> mediaObjectKeys) {
		String normalizedBody = bodyText == null ? "" : bodyText;
		String normalizedMedia = mediaObjectKeys == null ? "" : mediaObjectKeys.stream()
			.sorted().collect(Collectors.joining(","));
		String input = normalizedBody + SEPARATOR + normalizedMedia;
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte value : digest) {
				hex.append(String.format("%02x", value & 0xff));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required by the JDK", exception);
		}
	}
}
