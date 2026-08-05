package com.dnd.qello.common.error;

// 오류의 성격 분류. 운영 대응 방식의 기준.
//
// 재시도 가치가 있는지, 코드 수정이 필요한지를 구분하는 값.
public enum ErrorCategory {

	// 입력값 유효성 검증 실패. 형식, 범위, null 또는 빈 값
	VAL,

	// 도메인 불변식 위반. 같은 입력으로 재시도해도 해결 불가
	DOM,

	// 유즈케이스 흐름 실패. 대상의 상태나 선행 조건 불일치
	APP,

	// DB, 네트워크 등 인프라 문제. 재시도 후보
	INFRA,

	// 외부 시스템 연동 문제. 재시도 후보
	EXT
}
