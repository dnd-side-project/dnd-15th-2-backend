package com.dnd.qello.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.auth.domain.OperatorAccessLogEntry;
import com.dnd.qello.auth.repository.OperatorAccessLogRepository;

import lombok.RequiredArgsConstructor;

// 운영자 접근 기록의 단일 기록 지점(#375).
//
// 기록 실패를 다루는 방식이 둘이다(테스트 계획 D4).
// - 로그인 판정: 판정과 같은 트랜잭션에 참여하고 실패를 그대로 던진다. 기록 없는 세션을 만들지 않는다.
// - 요청 제한 거절·로그아웃·API 호출: 결과가 이미 정해졌으므로 따로 커밋하고, 실패해도 응답을 바꾸지 않는다.
@Component
@RequiredArgsConstructor
public class OperatorAccessLogRecorder {

	private static final Logger log = LoggerFactory.getLogger(OperatorAccessLogRecorder.class);

	private final OperatorAccessLogRepository repository;

	/**
	 * 호출자 트랜잭션 안에서 기록한다. 트랜잭션 없이 부르면 실패한다.
	 *
	 * <p>
	 * 로그인 실패는 {@code noRollbackFor}로 실패 카운터와 함께 커밋된다. insert가 실패하면 그 예외가 판정 전체를
	 * 롤백한다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordWithinTransaction(OperatorAccessLogEntry entry) {
		repository.append(entry);
	}

	/**
	 * 호출자 트랜잭션과 무관하게 바로 커밋한다. 실패하면 error 로그만 남기고 예외를 던지지 않는다.
	 *
	 * <p>
	 * 예외를 이 메서드 안에서 잡아도 rollback-only 표시가 생기지 않도록 트랜잭션을 열지 않는다. insert 한 건은
	 * auto-commit으로 원자적이다.
	 */
	// 바깥 트랜잭션에 합류하면 insert 실패가 그 트랜잭션을 rollback-only로 만든다. 그래서 트랜잭션 없이 실행한다.
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void recordIndependently(OperatorAccessLogEntry entry) {
		try {
			repository.append(entry);
		} catch (RuntimeException exception) {
			// 예외 메시지에는 SQL과 바인딩 값이 들어갈 수 있어 클래스 이름만 남긴다.
			log.atError()
					.addKeyValue("eventType", entry.eventType())
					.addKeyValue("requestPath", entry.request().requestPath())
					.addKeyValue("exception", exception.getClass().getName())
					.log("operator_access_log_write_failed");
		}
	}
}
