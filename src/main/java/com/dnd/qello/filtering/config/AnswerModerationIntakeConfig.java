package com.dnd.qello.filtering.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.dnd.qello.filtering.moderation.AnswerModerationJobIntakeService;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.FilterJobStatusHistoryRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link AnswerModerationJobIntakeService}는 deadlineWindow가 운영값으로 정해지기 전까지
 * 의도적으로 컴포넌트 스캔 대상이 아니었다(GitHub #125). deadlineWindow가 5분으로
 * 승인되어 이 설정에서만 빈으로 등록한다 — 실행(pipeline 호출)과 verdict 소비는 별도
 * worker가 맡고 이 클래스는 job intake만 담당하므로, 아직 배선되지 않은
 * {@code AnswerModerationExecutionWorker}와는 독립적으로 활성화할 수 있다.
 */
@Configuration
public class AnswerModerationIntakeConfig {

	@Bean
	public AnswerModerationJobIntakeService answerModerationJobIntakeService(
		FilterJobRepository filterJobRepository,
		FilterReleaseRepository filterReleaseRepository,
		FilterJobStatusHistoryRepository filterJobStatusHistoryRepository,
		OutboxEventRepository outboxEventRepository,
		ObjectMapper objectMapper,
		@Value("${qello.filtering.answer-moderation.deadline-window}") Duration deadlineWindow,
		PlatformTransactionManager transactionManager,
		Clock clock
	) {
		return new AnswerModerationJobIntakeService(filterJobRepository, filterReleaseRepository,
			filterJobStatusHistoryRepository, outboxEventRepository, objectMapper, deadlineWindow,
			transactionManager, clock);
	}
}
