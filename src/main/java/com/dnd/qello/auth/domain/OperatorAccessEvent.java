package com.dnd.qello.auth.domain;

// 운영자 접근 기록의 이벤트 유형(#375). 값은 V34의 ck_operator_access_log_event_type과 같아야 한다.
public enum OperatorAccessEvent {

	LOGIN_SUCCEEDED, LOGIN_FAILED, LOGOUT, API_CALL;

	// 운영자를 특정하지 못할 수 있는 것은 로그인 실패뿐이다(미존재 login_id, 요청 제한 거절).
	public boolean requiresOperator() {
		return this != LOGIN_FAILED;
	}
}
