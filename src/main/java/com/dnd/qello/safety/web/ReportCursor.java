package com.dnd.qello.safety.web;

import java.time.Instant;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** {@code /reports/me} 커서. {epochMilli}_{id} 형식의 불투명 문자열로 클라이언트에 노출한다. */
final class ReportCursor {

	private ReportCursor() {
	}

	record Position(Instant createdAt, long id) {
	}

	static String encode(Instant createdAt, long id) {
		return createdAt.toEpochMilli() + "_" + id;
	}

	static Position decode(String cursor) {
		try {
			String[] parts = cursor.split("_", 2);
			return new Position(Instant.ofEpochMilli(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
		} catch (RuntimeException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cursor가 유효하지 않습니다");
		}
	}
}
