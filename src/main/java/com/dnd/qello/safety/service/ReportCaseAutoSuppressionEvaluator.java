package com.dnd.qello.safety.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.dnd.qello.safety.repository.ReportCaseRepository;
import com.dnd.qello.safety.repository.SafetyRepository;

/**
 * 신고 접수 직후 자동 전역 숨김 조건을 평가한다(#156). 운영자 조치에 의한
 * 숨김은 {@link SafetyCaseResolutionService#resolveCase}가 이미 처리하므로
 * (#155), 이 클래스는 그 트리거를 자동으로 만드는 역할만 한다 — 숨김 로직
 * 자체는 재사용한다.
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

	public ReportCaseAutoSuppressionEvaluator(SafetyRepository safetyRepository,
		ReportCaseRepository reportCaseRepository, ManualReviewCaseRepository manualReviewCaseRepository,
		SafetyCaseResolutionService safetyCaseResolutionService, AutoSuppressionPolicy autoSuppressionPolicy) {
		this.safetyRepository = safetyRepository;
		this.reportCaseRepository = reportCaseRepository;
		this.manualReviewCaseRepository = manualReviewCaseRepository;
		this.safetyCaseResolutionService = safetyCaseResolutionService;
		this.autoSuppressionPolicy = autoSuppressionPolicy;
	}

	public void evaluate(long caseId, Long answerId, Instant now) {
		if (hasReachedDistinctReporterThreshold(caseId)) {
			safetyCaseResolutionService.resolveCase(caseId, ModerationDecision.ACTIONED, now);
			return;
		}
		if (answerId == null) {
			return;
		}
		findAlreadyFlaggedManualReviewCase(answerId).ifPresent(manualReviewCase -> {
			ReportCase current = reportCaseRepository.findById(caseId).orElseThrow();
			reportCaseRepository.update(current.withLinkedManualReviewCase(manualReviewCase.id()));
			safetyCaseResolutionService.resolveCase(caseId, ModerationDecision.ACTIONED, now);
		});
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
