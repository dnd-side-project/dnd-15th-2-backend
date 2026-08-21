package com.dnd.qello.safety.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.domain.ManualReviewCase;
import com.dnd.qello.filtering.domain.ManualReviewCaseStatus;
import com.dnd.qello.filtering.repository.ManualReviewCaseRepository;
import com.dnd.qello.safety.domain.AutoSuppressionPolicy;
import com.dnd.qello.safety.domain.ModerationDecision;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportCase;
import com.dnd.qello.safety.domain.ReportCaseSeverity;
import com.dnd.qello.safety.domain.ReportCaseStatus;
import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;
import com.dnd.qello.safety.repository.ReportCaseRepository;
import com.dnd.qello.safety.repository.SafetyRepository;

/**
 * 신고 접수 직후 자동 전역 숨김 조건을 평가한다(#156, CRITICAL 조건은 #157).
 * 운영자 조치에 의한 숨김은 {@link SafetyCaseResolutionService#resolveCase}가
 * 이미 처리하므로(#155), 이 클래스는 그 트리거를 자동으로 만드는 역할만
 * 한다 — 숨김 로직 자체는 재사용한다.
 *
 * 호출자({@link SafetyReportService#submit}) 트랜잭션 안에서 실행돼야 한다 —
 * 신고 저장과 자동 숨김이 같은 커밋 경계를 공유해야 반쪽 상태(신고는 저장,
 * 숨김은 유실)를 피할 수 있다.
 */
@Component
public class ReportCaseAutoSuppressionEvaluator {

	private final SafetyRepository safetyRepository;
	private final ReportCaseRepository reportCaseRepository;
	private final ManualReviewCaseRepository manualReviewCaseRepository;
	private final SafetyCaseResolutionService safetyCaseResolutionService;
	private final AutoSuppressionPolicy autoSuppressionPolicy;
	private final boolean criticalAutoSuppressEnabled;

	public ReportCaseAutoSuppressionEvaluator(SafetyRepository safetyRepository,
		ReportCaseRepository reportCaseRepository, ManualReviewCaseRepository manualReviewCaseRepository,
		SafetyCaseResolutionService safetyCaseResolutionService, AutoSuppressionPolicy autoSuppressionPolicy,
		@Value("${qello.safety.report-case.auto-suppress.critical-enabled:false}")
		boolean criticalAutoSuppressEnabled) {
		this.safetyRepository = safetyRepository;
		this.reportCaseRepository = reportCaseRepository;
		this.manualReviewCaseRepository = manualReviewCaseRepository;
		this.safetyCaseResolutionService = safetyCaseResolutionService;
		this.autoSuppressionPolicy = autoSuppressionPolicy;
		this.criticalAutoSuppressEnabled = criticalAutoSuppressEnabled;
	}

	/**
	 * severity는 이번 신고 접수 이후 사건의 최종 심각도다(신규 오픈 또는 승격
	 * 반영 완료 상태, {@link SafetyReportService} 참고). {@code
	 * critical-enabled} 플래그가 꺼져 있으면(기본값) CRITICAL 사건도 URGENT
	 * 큐 라우팅만 되고 이 조건으로는 자동 숨김되지 않는다 — 설계 문서 §4.1
	 * A안의 프로덕션 활성화는 법률·안전 검토 이후 사람이 결정한다(#157).
	 */
	public void evaluate(long caseId, Long answerId, ReportCaseSeverity severity, Instant now) {
		if (criticalAutoSuppressEnabled && severity == ReportCaseSeverity.CRITICAL) {
			resolveIfStillOpen(caseId, now);
			return;
		}
		if (hasReachedDistinctReporterThreshold(caseId)) {
			resolveIfStillOpen(caseId, now);
			return;
		}
		if (answerId == null) {
			return;
		}
		findAlreadyFlaggedManualReviewCase(answerId).ifPresent(manualReviewCase -> {
			// findByIdForUpdate로 잠근다 — 잠금 없이 읽으면 같은 사건을 동시에
			// escalate()하는 다른 트랜잭션의 커밋을 이 update()가 통째로 덮어써
			// 승격이 조용히 사라질 수 있다(escalateIfMoreSevere도 같은 잠금을 쓴다).
			ReportCase locked = reportCaseRepository.findByIdForUpdate(caseId).orElseThrow();
			if (locked.status() == ReportCaseStatus.RESOLVED) {
				return;
			}
			reportCaseRepository.update(locked.withLinkedManualReviewCase(manualReviewCase.id()));
			resolveIfStillOpen(caseId, now);
		});
	}

	// 자동 숨김은 신고 접수 트랜잭션의 부수효과다 — 서로 다른 두 신고가 거의
	// 동시에 같은 사건을 각자 종결 조건에 도달시키면 한쪽만 실제로 종결에
	// 성공하고 다른 쪽은 REPORT_CASE_ALREADY_RESOLVED를 받는다. 이 경합은
	// 자동 숨김의 목표(콘텐츠 숨김)가 이미 달성됐다는 뜻이라 조용히 넘어간다 —
	// 그대로 전파하면 신고 접수 자체가 롤백돼 정상적인 신고까지 사라진다.
	private void resolveIfStillOpen(long caseId, Instant now) {
		try {
			safetyCaseResolutionService.resolveCase(caseId, ModerationDecision.ACTIONED, now);
		} catch (SafetyException exception) {
			if (exception.getErrorCode() != SafetyErrorCode.REPORT_CASE_ALREADY_RESOLVED) {
				throw exception;
			}
		}
	}

	private boolean hasReachedDistinctReporterThreshold(long caseId) {
		List<Report> reports = safetyRepository.findReportsByCaseId(caseId);
		Set<Long> distinctReporters = reports.stream().map(Report::reporterId).collect(Collectors.toSet());
		return distinctReporters.size() >= autoSuppressionPolicy.distinctReporterThreshold();
	}

	// "이미 숨김·수동검토"는 OPEN이거나(아직 진행 중) RESOLVED+BLOCK(이미 차단
	// 판정)이다. RESOLVED+ALLOW(무혐의)는 트리거하지 않는다.
	private Optional<ManualReviewCase> findAlreadyFlaggedManualReviewCase(long answerId) {
		FilterTarget target = FilterTarget.of(FilterTargetType.ANSWER, answerId);
		return manualReviewCaseRepository.findLatestByTarget(target)
			.filter(manualReviewCase -> manualReviewCase.status() == ManualReviewCaseStatus.OPEN
				|| manualReviewCase.resolvedVerdict() == FilterVerdict.BLOCK);
	}
}
