package com.dnd.qello.common.web.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

// 모든 성공 응답의 본문 형식. 상태 코드와 무관하게 이 형식만 사용.
//
// ApiErrorResponse와 status, timestamp를 공유하고 그 사이 슬롯만 data로 갈린다.
// 성공에는 전할 메시지가 없으므로 message 필드를 두지 않는다.
//
// data가 null이어도 필드를 지운다(@JsonInclude) 설정을 붙이지 않는다.
// 키가 조건부로 사라지면 클라이언트가 존재 여부로 분기하게 된다.
public record ApiResponse<T>(
	ApiStatus status,
	T data,

	// 전역 Jackson 설정에 기대지 않고 ISO-8601 문자열을 계약으로 고정한다.
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	Instant timestamp
) {

	// timestamp는 호출자가 넘긴다.
	public static <T> ApiResponse<T> success(T data, Instant timestamp) {
		return new ApiResponse<>(ApiStatus.SUCCESS, data, timestamp);
	}
}
