package com.dnd.qello.filtering.domain;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// 운영자가 직접 입력한 행위 근거. 분류용 코드와 자유 서술을 함께 받는다.
//
// 코드만 받으면 나중에 "왜 그 코드를 골랐는지"를 알 수 없고, 서술만 받으면
// 집계가 불가능하다. 둘 다 필수인 이유다.
//
// 서버가 기본값을 채우는 생성자를 두지 않는다 — 채우는 순간 감사 이력에서
// "왜"가 사라진다(INV-APL-012).
public record OperatorReason(String code, String text) {

	private static final int CODE_MAX_LENGTH = 30;
	private static final int TEXT_MAX_LENGTH = 500;

	public OperatorReason {
		if (code == null || code.isBlank() || code.length() > CODE_MAX_LENGTH) {
			throw new FilteringException(FilteringErrorCode.INVALID_TEXT, "reasonCode", "reasonCode 값이 유효하지 않습니다");
		}
		if (text == null || text.isBlank() || text.length() > TEXT_MAX_LENGTH) {
			throw new FilteringException(FilteringErrorCode.INVALID_TEXT, "reasonText", "reasonText 값이 유효하지 않습니다");
		}
	}
}
