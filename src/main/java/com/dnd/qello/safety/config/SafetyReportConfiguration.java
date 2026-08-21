package com.dnd.qello.safety.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dnd.qello.safety.domain.AutoSuppressionPolicy;
import com.dnd.qello.safety.domain.CriticalReportQuotaPolicy;
import com.dnd.qello.safety.domain.EvidenceRetentionPolicy;
import com.dnd.qello.safety.domain.ReportRateLimitPolicy;
import com.dnd.qello.safety.domain.SlaPolicy;

// 운영 기본값 확정(#157, 결정 근거는 TASK.md Decisions 표 참고). rate limit·SLA는
// #156 개발 임시값을 그대로 운영값으로 채택했고, 자동 숨김 임계값만 5→3으로 낮췄다.
@Configuration
public class SafetyReportConfiguration {

	@Bean
	public ReportRateLimitPolicy reportRateLimitPolicy(
		@Value("${qello.safety.report.rate-limit.max-requests:10}") int maxRequestsPerWindow,
		@Value("${qello.safety.report.rate-limit.window-minutes:60}") long windowMinutes) {
		return new ReportRateLimitPolicy(maxRequestsPerWindow, Duration.ofMinutes(windowMinutes));
	}

	@Bean
	public SlaPolicy slaPolicy(
		@Value("${qello.safety.report-case.sla.standard-hours:72}") long standardHours,
		@Value("${qello.safety.report-case.sla.urgent-hours:4}") long urgentHours) {
		return new SlaPolicy(Duration.ofHours(standardHours), Duration.ofHours(urgentHours));
	}

	@Bean
	public AutoSuppressionPolicy autoSuppressionPolicy(
		@Value("${qello.safety.report-case.auto-suppress.reporter-threshold:3}") int distinctReporterThreshold) {
		return new AutoSuppressionPolicy(distinctReporterThreshold);
	}

	// 설계 문서 §4.1 남용 통제 (a). CRITICAL 하위 사유 신고만 대상(#157).
	@Bean
	public CriticalReportQuotaPolicy criticalReportQuotaPolicy(
		@Value("${qello.safety.report.critical-daily-quota.max-requests:5}") int maxPerDay) {
		return new CriticalReportQuotaPolicy(maxPerDay);
	}

	// legal_hold=true인 스냅샷은 이 기간과 무관하게 정리 배치에서 제외된다(#157).
	@Bean
	public EvidenceRetentionPolicy evidenceRetentionPolicy(
		@Value("${qello.safety.report.evidence.retention-days:180}") long retentionDays) {
		return new EvidenceRetentionPolicy(Duration.ofDays(retentionDays));
	}
}
