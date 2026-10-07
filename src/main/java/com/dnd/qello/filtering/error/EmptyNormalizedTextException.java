package com.dnd.qello.filtering.error;

// 정규화한 결과가 빈 문자열인 입력(#318). 판정할 대상이 없는 입력 오류라서, 닉네임 게이트는 이 타입만
// 골라 공급자 장애와 구분한다. 오류 코드는 REQUIRED_VALUE_MISSING을 그대로 쓴다. 같은 코드가 서버 측
// 검증에서도 나오므로 호출자는 코드가 아니라 이 타입으로 판별한다.
// FilteringException의 하위 타입이라 기존 catch (FilteringException) 경로의 동작은 바뀌지 않는다.
public class EmptyNormalizedTextException extends FilteringException {

	public EmptyNormalizedTextException(String field) {
		super(FilteringErrorCode.REQUIRED_VALUE_MISSING, field);
	}
}
