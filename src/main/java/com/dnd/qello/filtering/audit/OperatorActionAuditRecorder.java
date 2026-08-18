package com.dnd.qello.filtering.audit;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.filtering.domain.OperatorActionAudit;
import com.dnd.qello.filtering.domain.OperatorActionTargetType;
import com.dnd.qello.filtering.domain.OperatorActionType;
import com.dnd.qello.filtering.domain.OperatorReason;
import com.dnd.qello.filtering.repository.OperatorActionAuditRepository;

// 운영자 행위 감사의 단일 기록 지점(#113).
//
// MANDATORY 전파를 쓴다. 호출자가 이미 연 트랜잭션에 반드시 합류해야 하며,
// 트랜잭션 없이 호출되면 예외로 실패한다. 감사를 별도 트랜잭션으로 두면
// "결정은 커밋됐는데 근거는 사라진" 조합이 생기고, 그때 감사 이력의 부재가
// 행위가 없었음인지 기록에 실패했음인지 구분할 수 없게 된다.
@Component
public class OperatorActionAuditRecorder {

	private final OperatorActionAuditRepository repository;
	private final Clock clock;

	public OperatorActionAuditRecorder(OperatorActionAuditRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public OperatorActionAudit record(long operatorUserId, OperatorActionType actionType,
		OperatorActionTargetType targetType, String targetKey, OperatorReason reason, String policyVersion) {
		if (reason == null) {
			throw new IllegalArgumentException("reason은 필수입니다");
		}
		Instant now = Instant.now(clock);
		return repository.save(OperatorActionAudit.record(operatorUserId, actionType, targetType, targetKey,
			reason.code(), reason.text(), policyVersion, now));
	}
}
