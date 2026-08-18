package com.dnd.qello.filtering.moderation;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.filtering.domain.AppealAcceptance;
import com.dnd.qello.filtering.domain.AppealCase;
import com.dnd.qello.filtering.domain.AppealDecision;
import com.dnd.qello.filtering.domain.AppealWindow;
import com.dnd.qello.filtering.domain.FilterDecision;
import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.repository.AppealCaseRepository;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

// 작성자 이의제기의 유일한 진입점(#112). 접수, 검토자 결정, 접수 기간 연장을
// 모두 여기서만 수행한다.
//
// filtering.moderation 패키지에 두는 이유는 AnswerModerationEventPayloads
// (MODERATION_APPEAL_RESOLVED 페이로드)가 package-private이라 이 패키지 안에서만
// 그 계약을 재사용할 수 있기 때문이다(ManualReviewDecisionService와 같은 이유).
@Service
@Transactional(readOnly = true)
public class AppealCaseService {

	// 접수 기간은 설정이나 요청 파라미터로 주입받지 않는다. 주입 경로가 있으면
	// 그 경로 자체가 "기간을 6개월보다 줄이는 경로"가 된다(INV-APL-008, INV-APL-009).
	private static final AppealWindow WINDOW = AppealWindow.GLOBAL;

	private final AppealCaseRepository appealCaseRepository;
	private final FilterDecisionRepository filterDecisionRepository;
	private final FilterJobRepository filterJobRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final AppealTargetOwnershipChecker ownershipChecker;
	private final PublicationBlockChecker publicationBlockChecker;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public AppealCaseService(
		AppealCaseRepository appealCaseRepository,
		FilterDecisionRepository filterDecisionRepository,
		FilterJobRepository filterJobRepository,
		OutboxEventRepository outboxEventRepository,
		AppealTargetOwnershipChecker ownershipChecker,
		PublicationBlockChecker publicationBlockChecker,
		ObjectMapper objectMapper,
		Clock clock
	) {
		this.appealCaseRepository = appealCaseRepository;
		this.filterDecisionRepository = filterDecisionRepository;
		this.filterJobRepository = filterJobRepository;
		this.outboxEventRepository = outboxEventRepository;
		this.ownershipChecker = ownershipChecker;
		this.publicationBlockChecker = publicationBlockChecker;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	// 접수. 어떤 콜백도 발행하지 않고 어떤 공개 상태도 건드리지 않는다 —
	// 이의제기 중에도 콘텐츠는 비공개로 남아야 한다(INV-APL-003).
	//
	// 검사 순서에 의미가 있다. 소유권을 가장 먼저 확인해, 남의 콘텐츠에 대해
	// "그 decision이 존재하는지"조차 알려주지 않는다.
	@Transactional
	public AppealCase file(FilterTargetType targetType, long targetId, long filterDecisionId, long appellantUserId) {
		Instant now = Instant.now(clock);
		if (targetType != FilterTargetType.ANSWER) {
			throw new FilteringException(FilteringErrorCode.UNSUPPORTED_APPEAL_TARGET, "targetType",
				"이의제기는 답변 대상에만 접수할 수 있습니다");
		}
		if (!ownershipChecker.isOwnedBy(targetType, targetId, appellantUserId)) {
			throw new FilteringException(FilteringErrorCode.APPEAL_NOT_OWNED, "targetId");
		}

		FilterDecision decision = filterDecisionRepository.findById(filterDecisionId)
			.orElseThrow(() -> new FilteringException(FilteringErrorCode.FILTER_DECISION_NOT_FOUND, "filterDecisionId"));
		FilterTarget decisionTarget = requireTargetOf(decision);
		// decision이 다른 대상의 것이면 존재 자체를 알려주지 않는다.
		if (decisionTarget.targetType() != targetType || decisionTarget.targetId() != targetId) {
			throw new FilteringException(FilteringErrorCode.FILTER_DECISION_NOT_FOUND, "filterDecisionId");
		}
		if (decision.verdict() != FilterVerdict.BLOCK) {
			throw new FilteringException(FilteringErrorCode.APPEAL_TARGET_NOT_HIDDEN, "filterDecisionId");
		}

		appealCaseRepository.findByTargetAndFilterDecisionId(targetType, targetId, filterDecisionId)
			.ifPresent(existing -> {
				throw new FilteringException(FilteringErrorCode.DUPLICATE_CASE, "filterDecisionId",
					"이미 접수된 이의제기입니다");
			});

		AppealAcceptance acceptance = WINDOW.evaluate(decision.decidedAt(), now);
		if (!acceptance.accepted()) {
			throw new FilteringException(FilteringErrorCode.APPEAL_WINDOW_ELAPSED, "filterDecisionId");
		}

		try {
			return appealCaseRepository.save(
				AppealCase.file(targetType, targetId, filterDecisionId, appellantUserId, acceptance, WINDOW, now));
		} catch (DataIntegrityViolationException race) {
			// 위 조회와 이 저장 사이에 다른 트랜잭션이 먼저 접수했다.
			// uq_appeal_case_target_decision이 감지한 경쟁을 같은 오류로 변환한다(INV-APL-002).
			throw new FilteringException(FilteringErrorCode.DUPLICATE_CASE, "filterDecisionId",
				"이미 접수된 이의제기입니다", race);
		}
	}

	// 검토자 결정. case를 행 잠금으로 조회해 동시 결정을 직렬화한다 — 잠금이 없으면
	// 두 트랜잭션이 같은 OPEN case를 읽어 복원 콜백을 두 번 발행할 수 있다.
	//
	// OVERTURN_HIDDEN일 때만, 그리고 다른 공개 금지 사유가 없을 때만 복원 콜백을
	// 낸다. 차단 사유가 있으면 결정은 그대로 기록하되 콜백을 내지 않고 사유를 남긴다.
	@Transactional
	public AppealCase decide(long appealCaseId, AppealDecision decision, long operatorUserId) {
		Instant now = Instant.now(clock);
		AppealCase appealCase = appealCaseRepository.findByIdForUpdate(appealCaseId)
			.orElseThrow(() -> new FilteringException(FilteringErrorCode.APPEAL_CASE_NOT_FOUND, "appealCaseId"));

		String restoreBlockedReasonCode = null;
		if (decision == AppealDecision.OVERTURN_HIDDEN) {
			// 포트가 예외를 던지면 트랜잭션 전체가 롤백된다. "확인하지 못했다"를
			// "차단이 없다"로 해석해 복원 콜백을 내보내지 않는다(fail-closed).
			restoreBlockedReasonCode = publicationBlockChecker
				.findPublicationBlockReason(appealCase.targetType(), appealCase.targetId())
				.orElse(null);
		}

		AppealCase resolved = appealCaseRepository
			.save(appealCase.decide(decision, operatorUserId, now, restoreBlockedReasonCode));
		if (resolved.requiresRestoreCallback()) {
			outboxEventRepository.save(appealResolvedEvent(resolved, now));
		}
		return resolved;
	}

	// 법률·정책상 접수 기간을 연장한다. 도메인이 현재 만료보다 이르거나 같은 값을
	// 거절하므로 이 경로로 기간이 줄어들 수 없다(INV-APL-008, INV-APL-009).
	@Transactional
	public AppealCase extendExpiry(long appealCaseId, Instant newExpiresAt) {
		AppealCase appealCase = appealCaseRepository.findByIdForUpdate(appealCaseId)
			.orElseThrow(() -> new FilteringException(FilteringErrorCode.APPEAL_CASE_NOT_FOUND, "appealCaseId"));
		return appealCaseRepository.save(appealCase.extendExpiry(newExpiresAt));
	}

	public List<AppealCase> findMine(long appellantUserId) {
		return appealCaseRepository.findByAppellantUserId(appellantUserId);
	}

	public List<AppealCase> findQueue(int limit) {
		return appealCaseRepository.findOpenQueue(limit);
	}

	// decision이 가리키는 job의 target. decision은 job 없이 존재할 수 없으므로,
	// 여기서 job이 없다면 참조 무결성이 이미 깨진 상태다.
	private FilterTarget requireTargetOf(FilterDecision decision) {
		return filterJobRepository.findById(decision.filterJobId())
			.map(FilterJob::target)
			.orElseThrow(() -> new FilteringException(
				FilteringErrorCode.FILTER_DECISION_NOT_FOUND, "filterDecisionId"));
	}

	private OutboxEvent appealResolvedEvent(AppealCase appealCase, Instant now) {
		FilterTarget target = filterDecisionRepository.findById(appealCase.filterDecisionId())
			.map(this::requireTargetOf)
			.orElseThrow(() -> new FilteringException(
				FilteringErrorCode.FILTER_DECISION_NOT_FOUND, "filterDecisionId"));
		AnswerModerationEventPayloads.AppealResolved payload = new AnswerModerationEventPayloads.AppealResolved(
			appealCase.id(), appealCase.filterDecisionId(), target.targetType(), target.targetId(),
			target.targetVersion());
		return OutboxEvent.pending(OutboxAggregateType.APPEAL_CASE, appealCase.id(),
			OutboxEventType.MODERATION_APPEAL_RESOLVED,
			AnswerModerationEventPayloads.appealResolvedDedupKey(appealCase.id()),
			AnswerModerationEventPayloads.toJson(objectMapper, payload), now);
	}
}
