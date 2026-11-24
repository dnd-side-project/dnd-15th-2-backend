package com.dnd.qello.auth.repository;

import com.dnd.qello.auth.domain.OperatorAccessLogEntry;

// 운영자 접근 기록 Port(#375).
//
// append-only 원장이라 추가만 둔다. 수정·삭제 메서드를 더하지 않는다. 조회가 필요해지면
// 읽기 메서드만 더한다(OperatorAccessLogAppendOnlyTest가 이 계약을 고정한다).
public interface OperatorAccessLogRepository {

	void append(OperatorAccessLogEntry entry);
}
