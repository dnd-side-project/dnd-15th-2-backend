package com.dnd.qello.safety.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportCase;
import com.dnd.qello.safety.domain.ReportCaseEvent;
import com.dnd.qello.safety.domain.ReportCaseEventType;
import com.dnd.qello.safety.domain.ReportCaseQueue;
import com.dnd.qello.safety.domain.ReportCaseSeverity;
import com.dnd.qello.safety.domain.ReportContentHasher;
import com.dnd.qello.safety.domain.ReportContentSnapshot;
import com.dnd.qello.safety.domain.ReportRateLimitPolicy;
import com.dnd.qello.safety.domain.ReportSubmission;
import com.dnd.qello.safety.domain.ReportTargetSnapshot;
import com.dnd.qello.safety.domain.ReportTargetType;
import com.dnd.qello.safety.domain.SlaPolicy;
import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;
import com.dnd.qello.safety.repository.ReportCaseEventRepository;
import com.dnd.qello.safety.repository.ReportCaseRepository;
import com.dnd.qello.safety.repository.ReportContentSnapshotRepository;
import com.dnd.qello.safety.repository.ReportTargetRepository;
import com.dnd.qello.safety.repository.SafetyRepository;

/**
 * 신고 접수 흐름을 조립한다. Foundation(#153)이 만든 {@link ReportCase}/
 * {@link ReportContentSnapshot}/{@link ReportCaseEvent}/{@link Report#attachToCase}
 * 위에, 이 서비스가 처음으로 실제 사건 병합·증거 캡처·중복 판정을 수행한다.
 */
@Service
public class SafetyReportService {

	private static final int MAX_CASE_MERGE_ATTEMPTS = 3;

	private final SafetyRepository safetyRepository;
	private final ReportCaseRepository reportCaseRepository;
	private final ReportContentSnapshotRepository reportContentSnapshotRepository;
	private final ReportCaseEventRepository reportCaseEventRepository;
	private final ReportTargetRepository reportTargetRepository;
	private final SafetyService safetyService;
	private final ReportRateLimitPolicy rateLimitPolicy;
	private final SlaPolicy slaPolicy;
	private final ReportCaseAutoSuppressionEvaluator autoSuppressionEvaluator;

	public SafetyReportService(SafetyRepository safetyRepository, ReportCaseRepository reportCaseRepository,
		ReportContentSnapshotRepository reportContentSnapshotRepository,
		ReportCaseEventRepository reportCaseEventRepository, ReportTargetRepository reportTargetRepository,
		SafetyService safetyService, ReportRateLimitPolicy rateLimitPolicy, SlaPolicy slaPolicy,
		ReportCaseAutoSuppressionEvaluator autoSuppressionEvaluator) {
		this.safetyRepository = safetyRepository;
		this.reportCaseRepository = reportCaseRepository;
		this.reportContentSnapshotRepository = reportContentSnapshotRepository;
		this.reportCaseEventRepository = reportCaseEventRepository;
		this.reportTargetRepository = reportTargetRepository;
		this.safetyService = safetyService;
		this.rateLimitPolicy = rateLimitPolicy;
		this.slaPolicy = slaPolicy;
		this.autoSuppressionEvaluator = autoSuppressionEvaluator;
	}

	@Transactional
	public ReportOutcome submitAnswerReport(long reporterId, long answerId, ReportSubmission submission,
		boolean blockAuthor, Instant now) {
		return submit(ReportTargetType.ANSWER, answerId, reporterId, submission, blockAuthor, now);
	}

	@Transactional
	public ReportOutcome submitPostReport(long reporterId, long postId, ReportSubmission submission,
		boolean blockAuthor, Instant now) {
		return submit(ReportTargetType.DIRECTION_POST, postId, reporterId, submission, blockAuthor, now);
	}

	@Transactional
	public ReportOutcome submitUserReport(long reporterId, long targetUserId, ReportSubmission submission,
		boolean blockAuthor, Instant now) {
		return submit(ReportTargetType.USER, targetUserId, reporterId, submission, blockAuthor, now);
	}

	@Transactional(readOnly = true)
	public Report requireOwnReport(long reportId, long viewerId) {
		return safetyRepository.findReportById(reportId)
			.filter(report -> report.reporterId() == viewerId)
			.orElseThrow(() -> new SafetyException(SafetyErrorCode.REPORT_NOT_FOUND, null, "신고를 찾을 수 없습니다"));
	}

	@Transactional(readOnly = true)
	public List<Report> findMyReports(long reporterId, Instant cursorCreatedAt, Long cursorId, int limit) {
		return safetyRepository.findReportsByReporter(reporterId, cursorCreatedAt, cursorId, limit);
	}

	private ReportOutcome submit(ReportTargetType type, long targetId, long reporterId,
		ReportSubmission submission, boolean blockAuthor, Instant now) {
		// 신고자 단위로 직렬화해 rate limit count-then-insert와 findOpenReport-then-insert
		// 경합을 없앤다 — 뒤따르는 트랜잭션은 앞선 트랜잭션의 커밋 이후에만 진행된다.
		safetyRepository.acquireReporterSubmissionLock(reporterId);
		ReportTargetSnapshot target = resolveTarget(type, targetId, reporterId, now)
			.orElseThrow(() -> new SafetyException(
				SafetyErrorCode.REPORT_TARGET_NOT_FOUND, null, "신고 대상을 찾을 수 없습니다"));
		if (target.authorId() == reporterId) {
			throw new SafetyException(SafetyErrorCode.SELF_REPORT_NOT_ALLOWED, null, "자기 자신을 신고할 수 없습니다");
		}

		Long targetUserId = type == ReportTargetType.USER ? targetId : null;
		Long directionPostId = type == ReportTargetType.DIRECTION_POST ? targetId : null;
		Long answerId = type == ReportTargetType.ANSWER ? targetId : null;

		Optional<Report> open = safetyRepository.findOpenReport(reporterId, targetUserId, directionPostId, answerId);
		if (open.isPresent()) {
			return new ReportOutcome(open.get(), true);
		}

		String currentHash = ReportContentHasher.hash(target.bodyText(), target.mediaObjectKeys());
		Optional<ReportOutcome> suppressed = suppressIfDuplicateOfClosed(
			reporterId, targetUserId, directionPostId, answerId, currentHash, now);
		if (suppressed.isPresent()) {
			return suppressed.get();
		}

		// 멱등 반환·억제 경로는 새 신고를 만들지 않으므로 한도 검사에서 면제한다.
		enforceRateLimit(reporterId, now);
		ReportCaseSeverity severity = ReportCaseSeverity.of(submission.subReason());
		long caseId = mergeCase(targetUserId, directionPostId, answerId, severity, now);
		Report toSave = newReport(type, reporterId, targetId, submission, now).attachToCase(caseId);
		Report saved = safetyRepository.saveReport(toSave);

		ReportContentSnapshot snapshot = ReportContentSnapshot.capture(saved.id(), now, type,
			targetId, target.authorId(), target.bodyText(), target.mediaObjectKeys(), target.editCount(),
			target.contentPublishedAt());
		reportContentSnapshotRepository.save(snapshot);

		if (blockAuthor) {
			safetyService.block(reporterId, target.authorId(), now);
		}

		autoSuppressionEvaluator.evaluate(caseId, answerId, now);

		return new ReportOutcome(saved, false);
	}

	private Optional<ReportOutcome> suppressIfDuplicateOfClosed(long reporterId, Long targetUserId,
		Long directionPostId, Long answerId, String currentHash, Instant now) {
		Optional<Report> lastClosed = safetyRepository.findMostRecentClosedReport(
			reporterId, targetUserId, directionPostId, answerId);
		if (lastClosed.isEmpty()) {
			return Optional.empty();
		}
		Report closed = lastClosed.get();
		Optional<ReportContentSnapshot> closedSnapshot = reportContentSnapshotRepository.findByReportId(closed.id());
		if (closedSnapshot.isEmpty() || !closedSnapshot.get().contentHash().equals(currentHash)) {
			return Optional.empty();
		}
		if (closed.caseId() != null) {
			reportCaseEventRepository.save(
				ReportCaseEvent.of(closed.caseId(), ReportCaseEventType.DUPLICATE_SUPPRESSED, now));
		}
		return Optional.of(new ReportOutcome(closed, true));
	}

	/**
	 * ON CONFLICT DO NOTHING(승자)과 재조회(패자)로 사건을 하나로 수렴시킨다(INV-RPT-001).
	 * 재조회가 비면(그 사이 사건이 종결됨) 짧게 재시도한다 — 그래도 실패하면 일시적
	 * 경합으로 보고 SAF-INFRA-002를 반환한다.
	 *
	 * 기존 열린 사건에 더 심각한 신고가 붙으면(NORMAL→CRITICAL만, 강등은 없다)
	 * 행 잠금 후 승격한다(#156) — 잠금 없이 read-then-write하면 두 CRITICAL 신고가
	 * 동시에 붙을 때 ESCALATED 이벤트가 중복되거나 유실될 수 있다.
	 */
	private long mergeCase(
		Long targetUserId, Long directionPostId, Long answerId, ReportCaseSeverity severity, Instant now) {
		ReportCaseQueue queue = ReportCaseQueue.of(severity);
		Instant slaDueAt = now.plus(slaPolicy.of(queue));
		for (int attempt = 0; attempt < MAX_CASE_MERGE_ATTEMPTS; attempt++) {
			Optional<ReportCase> opened = reportCaseRepository.tryOpen(
				ReportCase.open(targetUserId, directionPostId, answerId, severity, queue, now, slaDueAt));
			if (opened.isPresent()) {
				reportCaseEventRepository.save(
					ReportCaseEvent.of(opened.get().id(), ReportCaseEventType.CASE_OPENED, now));
				return opened.get().id();
			}
			Optional<ReportCase> existing = reportCaseRepository.findOpenByTarget(
				targetUserId, directionPostId, answerId);
			if (existing.isPresent()) {
				escalateIfMoreSevere(existing.get(), severity, now);
				reportCaseEventRepository.save(
					ReportCaseEvent.of(existing.get().id(), ReportCaseEventType.REPORT_ATTACHED, now));
				return existing.get().id();
			}
		}
		throw new SafetyException(SafetyErrorCode.CASE_MERGE_CONFLICT, null, "사건 병합에 반복적으로 실패했습니다");
	}

	private void escalateIfMoreSevere(ReportCase existing, ReportCaseSeverity incomingSeverity, Instant now) {
		if (incomingSeverity != ReportCaseSeverity.CRITICAL || existing.severity() == ReportCaseSeverity.CRITICAL) {
			return;
		}
		// 행 잠금 후 다시 확인한다 — 잠그기 전에 읽은 existing은 경합 중인 다른
		// 트랜잭션이 이미 승격시킨 값일 수 있다.
		ReportCase locked = reportCaseRepository.findByIdForUpdate(existing.id()).orElseThrow(
			() -> new SafetyException(SafetyErrorCode.INVALID_ID, "caseId", "사건을 찾을 수 없습니다"));
		if (locked.severity() == ReportCaseSeverity.CRITICAL) {
			return;
		}
		Instant escalatedSlaDueAt = now.plus(slaPolicy.of(ReportCaseQueue.URGENT));
		reportCaseRepository.update(locked.escalate(now, escalatedSlaDueAt));
		reportCaseEventRepository.save(ReportCaseEvent.of(locked.id(), ReportCaseEventType.ESCALATED, now));
	}

	private void enforceRateLimit(long reporterId, Instant now) {
		Instant windowStart = now.minus(rateLimitPolicy.window());
		int count = safetyRepository.countReportsByReporterSince(reporterId, windowStart);
		if (count >= rateLimitPolicy.maxRequestsPerWindow()) {
			throw new SafetyException(SafetyErrorCode.REPORT_RATE_LIMIT_EXCEEDED, null, "신고 요청이 너무 많습니다");
		}
	}

	private Optional<ReportTargetSnapshot> resolveTarget(
		ReportTargetType type, long targetId, long viewerId, Instant now) {
		return switch (type) {
			case ANSWER -> reportTargetRepository.findViewableAnswer(targetId, viewerId, now);
			case DIRECTION_POST -> reportTargetRepository.findViewablePost(targetId, viewerId, now);
			case USER -> reportTargetRepository.findViewableUser(targetId, viewerId);
		};
	}

	private static Report newReport(
		ReportTargetType type, long reporterId, long targetId, ReportSubmission submission, Instant now) {
		String reasonCode = submission.reason().name();
		String subReasonCode = submission.subReason() == null ? null : submission.subReason().name();
		String detail = submission.detail();
		return switch (type) {
			case ANSWER -> Report.forAnswer(reporterId, targetId, reasonCode, subReasonCode, detail, now);
			case DIRECTION_POST -> Report.forPost(reporterId, targetId, reasonCode, subReasonCode, detail, now);
			case USER -> Report.forUser(reporterId, targetId, reasonCode, subReasonCode, detail, now);
		};
	}
}
