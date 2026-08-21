package com.dnd.qello.safety.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.safety.domain.ModerationDecision;
import com.dnd.qello.safety.domain.ModerationReview;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportCase;
import com.dnd.qello.safety.domain.ReportCaseEvent;
import com.dnd.qello.safety.domain.ReportCaseEventType;
import com.dnd.qello.safety.domain.ReportCaseQueue;
import com.dnd.qello.safety.domain.ReportCaseStatus;
import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;
import com.dnd.qello.safety.repository.ReportCaseEventRepository;
import com.dnd.qello.safety.repository.ReportCaseRepository;
import com.dnd.qello.safety.repository.SafetyRepository;

/**
 * 운영자 판정 API의 유스케이스를 조립한다(#156). 사건 종결(ACTIONED/NO_VIOLATION)
 * 자체는 {@link SafetyCaseResolutionService#resolveCase}(#155)를 그대로 재사용하고
 * ­여기서는 그 앞뒤로 운영자 전용 관심사(행 잠금 필요한 상태 전이, 신고별
 * moderation_review 기록)만 얹는다.
 */
@Service
public class OperatorReportCaseService {

	private final ReportCaseRepository reportCaseRepository;
	private final ReportCaseEventRepository reportCaseEventRepository;
	private final SafetyRepository safetyRepository;
	private final SafetyCaseResolutionService safetyCaseResolutionService;
	private final AnswerRepository answerRepository;

	public OperatorReportCaseService(ReportCaseRepository reportCaseRepository,
		ReportCaseEventRepository reportCaseEventRepository, SafetyRepository safetyRepository,
		SafetyCaseResolutionService safetyCaseResolutionService, AnswerRepository answerRepository) {
		this.reportCaseRepository = reportCaseRepository;
		this.reportCaseEventRepository = reportCaseEventRepository;
		this.safetyRepository = safetyRepository;
		this.safetyCaseResolutionService = safetyCaseResolutionService;
		this.answerRepository = answerRepository;
	}

	public List<ReportCase> findQueue(ReportCaseQueue queue, Instant cursorSlaDueAt, Long cursorId, int limit) {
		return reportCaseRepository.findQueue(queue, cursorSlaDueAt, cursorId, limit);
	}

	@Transactional
	public ReportCase startReview(long caseId) {
		ReportCase locked = lockOrThrow(caseId);
		return reportCaseRepository.update(locked.startReview());
	}

	/** decision은 ACTIONED 또는 NO_VIOLATION만 허용한다 — MORE_INFO_REQUIRED는 {@link #requestMoreInfo}. */
	@Transactional
	public ReportCase decide(long caseId, ModerationDecision decision, long operatorUserId, String internalNote,
		Instant now) {
		if (decision == ModerationDecision.MORE_INFO_REQUIRED) {
			throw new SafetyException(SafetyErrorCode.INVALID_REPORT_STATUS, "decision",
				"MORE_INFO_REQUIRED는 별도 API를 사용합니다");
		}
		ReportCase resolved = safetyCaseResolutionService.resolveCase(caseId, decision, now);
		recordModerationReview(caseId, decision, operatorUserId, internalNote, now);
		return resolved;
	}

	@Transactional
	public ReportCase requestMoreInfo(long caseId, long operatorUserId, String internalNote, Instant now) {
		ReportCase locked = lockOrThrow(caseId);
		ReportCase updated = reportCaseRepository.update(locked.requestMoreInfo(now));
		reportCaseEventRepository.save(ReportCaseEvent.of(caseId, ReportCaseEventType.MORE_INFO_REQUESTED, now));
		recordModerationReview(caseId, ModerationDecision.MORE_INFO_REQUIRED, operatorUserId, internalNote, now);
		return updated;
	}

	@Transactional
	public Answer restore(long caseId, Instant now) {
		ReportCase locked = lockOrThrow(caseId);
		if (locked.answerId() == null) {
			throw new SafetyException(
				SafetyErrorCode.INVALID_REPORT_TARGET, "caseId", "답변을 대상으로 하는 사건이 아닙니다");
		}
		// 같은 답변을 가리키는 사건은 여러 개(재발마다 새 사건, INV-RPT-007) 있을 수
		// 있다 — answerId만 확인하면 무관한 사건 id로도 복원이 통과해 그 답변을 실제로
		// 숨긴 판정과 다른 사건을 근거로 복원하는 권한 우회가 생긴다. 이 사건 자체가
		// ACTIONED로 종결됐는지까지 확인한다.
		if (locked.status() != ReportCaseStatus.RESOLVED || locked.decision() != ModerationDecision.ACTIONED) {
			throw new SafetyException(
				SafetyErrorCode.INVALID_REPORT_STATUS, "caseId", "ACTIONED로 종결된 사건만 복원할 수 있습니다");
		}
		Answer answer = answerRepository.findById(locked.answerId())
			.orElseThrow(() -> new SafetyException(
				SafetyErrorCode.REPORT_TARGET_NOT_FOUND, "answerId", "복원할 답변을 찾을 수 없습니다"));
		return answerRepository.save(answer.restore(now));
	}

	private ReportCase lockOrThrow(long caseId) {
		return reportCaseRepository.findByIdForUpdate(caseId)
			.orElseThrow(() -> new SafetyException(SafetyErrorCode.INVALID_ID, "caseId", "사건을 찾을 수 없습니다"));
	}

	// 사건에 묶인 신고마다 하나씩 감사 기록을 남긴다 — moderation_review는 report_id
	// 단위 테이블이라(pre-case 시절 설계) 사건 단위로는 저장할 수 없다.
	private void recordModerationReview(
		long caseId, ModerationDecision decision, long operatorUserId, String internalNote, Instant now) {
		for (Report report : safetyRepository.findReportsByCaseId(caseId)) {
			safetyRepository.saveReview(new ModerationReview(
				null, report.id(), operatorUserId, decision, decision.name(), internalNote, now));
		}
	}
}
