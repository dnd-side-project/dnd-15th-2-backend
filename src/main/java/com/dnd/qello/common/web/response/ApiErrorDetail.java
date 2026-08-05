package com.dnd.qello.common.web.response;

// 오류 응답의 상세 정보.
//
// code   - {BC}-{CATEGORY}-{SEQ} 형식의 오류 코드
// field  - 실패한 값의 이름. 값 단위 실패가 아니면 null
// reason - 어떤 규칙을 어겼는지 설명하는 안전한 메시지
public record ApiErrorDetail(
	String code,
	String field,
	String reason
) {
}
