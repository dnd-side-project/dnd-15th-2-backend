package com.dnd.qello.filtering.repository;

import java.util.List;

import com.dnd.qello.filtering.domain.OperatorActionAudit;
import com.dnd.qello.filtering.domain.OperatorActionTargetType;

// append-only 원장이라 save와 조회만 둔다. update·delete 메서드를 제공하지
// 않는 것 자체가 계약이다.
public interface OperatorActionAuditRepository {

	OperatorActionAudit save(OperatorActionAudit audit);

	/** 대상 하나의 변경 이력을 오래된 순으로 반환한다. */
	List<OperatorActionAudit> findByTarget(OperatorActionTargetType targetType, String targetKey);
}
