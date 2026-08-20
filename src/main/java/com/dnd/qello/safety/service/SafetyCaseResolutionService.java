package com.dnd.qello.safety.service;

import java.time.Instant;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.safety.domain.ModerationDecision;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportCase;
import com.dnd.qello.safety.domain.ReportCaseEvent;
import com.dnd.qello.safety.domain.ReportCaseEventType;
import com.dnd.qello.safety.domain.ReportStatus;
import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;
import com.dnd.qello.safety.repository.ReportCaseEventRepository;
import com.dnd.qello.safety.repository.ReportCaseRepository;
import com.dnd.qello.safety.repository.SafetyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 신고 사건 종결의 유일한 진입점(#155). REST API는 없다 — 운영자 판정 API(#156)가
 * 나중에 이 내부 메서드를 호출한다(TASK.md "Scope decision" 참고).
 *
 * 한 트랜잭션 안에서: 사건 종결 → (ACTIONED면) 전역 숨김 부수효과 → 신고자 수만큼
 * outbox 이벤트 발행을 모두 수행한다. 전역 숨김·알림 REVOKE·outbox 발행이 같은
 * 트랜잭션 경계를 공유해야 하므로 나누지 않는다.
 */
@Service
public class SafetyCaseResolutionService {

	private static final Set<ModerationDecision> TERMINAL_DECISIONS =
		Set.of(ModerationDecision.ACTIONED, ModerationDecision.NO_VIOLATION);

	private final ReportCaseRepository reportCaseRepository;
	private final SafetyRepository safetyRepository;
	private final ReportCaseEventRepository reportCaseEventRepository;
	private final AnswerRepository answerRepository;
	private final NotificationRepository notificationRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;

	public SafetyCaseResolutionService(ReportCaseRepository reportCaseRepository, SafetyRepository safetyRepository,
		ReportCaseEventRepository reportCaseEventRepository, AnswerRepository answerRepository,
		NotificationRepository notificationRepository, OutboxEventRepository outboxEventRepository,
		ObjectMapper objectMapper) {
		this.reportCaseRepository = reportCaseRepository;
		this.safetyRepository = safetyRepository;
		this.reportCaseEventRepository = reportCaseEventRepository;
		this.answerRepository = answerRepository;
		this.notificationRepository = notificationRepository;
		this.outboxEventRepository = outboxEventRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public ReportCase resolveCase(long caseId, ModerationDecision decision, Instant now) {
		if (!TERMINAL_DECISIONS.contains(decision)) {
			throw new SafetyException(SafetyErrorCode.INVALID_REPORT_STATUS, "decision",
				"사건 종결은 ACTIONED 또는 NO_VIOLATION만 허용합니다");
		}

		// 행 잠금으로 동시 종결 시도를 직렬화한다 — 잠금이 없으면 두 트랜잭션이 같은
		// OPEN/UNDER_REVIEW 사건을 읽어 각각 종결과 outbox 발행을 중복 수행할 수 있다.
		ReportCase locked = reportCaseRepository.findByIdForUpdate(caseId)
			.orElseThrow(() -> new SafetyException(SafetyErrorCode.INVALID_ID, "caseId", "사건을 찾을 수 없습니다"));
		ReportCase resolved = reportCaseRepository.update(locked.resolve(decision, now));
		reportCaseEventRepository.save(ReportCaseEvent.of(resolved.id(), ReportCaseEventType.RESOLVED, now));

		if (decision == ModerationDecision.ACTIONED && resolved.answerId() != null) {
			suppressAnswer(resolved.answerId(), now);
		}

		ReportStatus reportStatus = toReportStatus(decision);
		for (Report report : safetyRepository.findReportsByCaseId(caseId)) {
			safetyRepository.updateReport(report.resolve(reportStatus, now));
			outboxEventRepository.save(reportResolvedEvent(report, now));
		}

		return resolved;
	}

	// 운영자 조치(ACTIONED)로 판정된 답변만 전역 숨김한다 — 자동 임계값 숨김(R04)은
	// 이 이슈 밖이다. 대상이 질문글·사용자면 이 이슈가 정의한 숨김 대상이 아니므로
	// 아무 것도 하지 않는다.
	private void suppressAnswer(long answerId, Instant now) {
		Answer answer = answerRepository.findById(answerId)
			.orElseThrow(() -> new SafetyException(
				SafetyErrorCode.REPORT_TARGET_NOT_FOUND, "answerId", "숨길 답변을 찾을 수 없습니다"));
		answerRepository.save(answer.hide(now));
		notificationRepository.revokeByAnswerId(answerId);
		notificationRepository.cancelDeliveriesByAnswerId(answerId);
	}

	private OutboxEvent reportResolvedEvent(Report report, Instant now) {
		ReportResolutionEventPayloads.ReportResolved payload =
			new ReportResolutionEventPayloads.ReportResolved(report.id());
		return OutboxEvent.pending(OutboxAggregateType.REPORT, report.id(), OutboxEventType.REPORT_RESOLVED,
			ReportResolutionEventPayloads.reportResolvedDedupKey(report.id()),
			ReportResolutionEventPayloads.toJson(objectMapper, payload), now);
	}

	private static ReportStatus toReportStatus(ModerationDecision decision) {
		return decision == ModerationDecision.ACTIONED ? ReportStatus.ACTIONED : ReportStatus.NO_VIOLATION;
	}
}
