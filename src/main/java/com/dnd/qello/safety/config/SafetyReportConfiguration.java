package com.dnd.qello.safety.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dnd.qello.safety.domain.ReportRateLimitPolicy;

// 실제 운영 수치는 미정이며(설계 문서 §12), 기본값은 개발 편의를 위한 임시값이다.
@Configuration
public class SafetyReportConfiguration {

	@Bean
	public ReportRateLimitPolicy reportRateLimitPolicy(
		@Value("${qello.safety.report.rate-limit.max-requests:10}") int maxRequestsPerWindow,
		@Value("${qello.safety.report.rate-limit.window-minutes:60}") long windowMinutes) {
		return new ReportRateLimitPolicy(maxRequestsPerWindow, Duration.ofMinutes(windowMinutes));
	}
}
