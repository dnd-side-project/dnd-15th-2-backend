/*
 * Created at: 2026-11-22T22:12:29+09:00
 * Source scenario: TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-015
 */
package com.dnd.qello.auth.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;

import com.dnd.qello.auth.domain.OperatorAccessLogEntry;
import com.dnd.qello.auth.domain.OperatorAccessRequest;
import com.dnd.qello.auth.repository.OperatorAccessLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class OperatorAccessLogRecorderTest {

	private static final Instant NOW = Instant.parse("2026-11-22T13:00:00Z");
	private static final OperatorAccessLogEntry ENTRY = OperatorAccessLogEntry.apiCall(
			42L, new OperatorAccessRequest("192.0.2.10", "GET", "/api/v1/operator/report-cases"), 200, NOW);

	private final FakeRepository repository = new FakeRepository();
	private final OperatorAccessLogRecorder recorder = new OperatorAccessLogRecorder(repository);

	private Logger recorderLogger;
	private ListAppender<ILoggingEvent> logAppender;

	@BeforeEach
	void attachLogAppender() {
		recorderLogger = (Logger) LoggerFactory.getLogger(OperatorAccessLogRecorder.class);
		logAppender = new ListAppender<>();
		logAppender.start();
		recorderLogger.addAppender(logAppender);
	}

	@AfterEach
	void detachLogAppender() {
		recorderLogger.detachAppender(logAppender);
	}

	@Test
	@DisplayName("독립 기록은 저장소가 정상이면 항목을 한 번 추가한다")
	void appendsOnceWhenRepositorySucceeds() {
		recorder.recordIndependently(ENTRY);

		assertThat(repository.entries).containsExactly(ENTRY);
		assertThat(logAppender.list).isEmpty();
	}

	@Test
	@DisplayName("독립 기록은 저장소 예외를 던지지 않고 예외 클래스만 담은 error 로그 한 줄을 남긴다")
	void swallowsRepositoryFailureWithSingleErrorLog() {
		repository.failure = new DataAccessResourceFailureException("INSERT ... values (secret-bound-value)");

		assertThatCode(() -> recorder.recordIndependently(ENTRY)).doesNotThrowAnyException();

		assertThat(repository.entries).isEmpty();
		assertThat(logAppender.list).singleElement().satisfies(event -> {
			assertThat(event.getLevel()).isEqualTo(Level.ERROR);
			assertThat(event.getFormattedMessage()).isEqualTo("operator_access_log_write_failed");
			assertThat(event.getKeyValuePairs())
					.anySatisfy(pair -> assertThat(pair.value)
							.isEqualTo(DataAccessResourceFailureException.class.getName()));
			// 예외 메시지(SQL·바인딩 값)와 스택은 로그에 넣지 않는다.
			assertThat(event.getThrowableProxy()).isNull();
			assertThat(event.getKeyValuePairs()).noneSatisfy(
					pair -> assertThat(String.valueOf(pair.value)).contains("secret-bound-value"));
		});
	}

	@Test
	@DisplayName("트랜잭션 안의 기록은 저장소 예외를 그대로 던진다")
	void propagatesFailureWithinTransaction() {
		repository.failure = new DataAccessResourceFailureException("simulated");

		assertThatThrownBy(() -> recorder.recordWithinTransaction(ENTRY))
				.isInstanceOf(DataAccessResourceFailureException.class);
	}

	private static final class FakeRepository implements OperatorAccessLogRepository {

		private final List<OperatorAccessLogEntry> entries = new ArrayList<>();
		private RuntimeException failure;

		@Override
		public void append(OperatorAccessLogEntry entry) {
			if (failure != null) {
				throw failure;
			}
			entries.add(entry);
		}
	}
}
