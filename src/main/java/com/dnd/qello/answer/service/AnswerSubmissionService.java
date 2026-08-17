package com.dnd.qello.answer.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.direction.config.DirectionPostProperties;
import com.dnd.qello.direction.domain.DirectionRequestFingerprint;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.moderation.AnswerModerationIntake;
import com.dnd.qello.filtering.moderation.ModerationLanguage;

/**
 * 답변 제출의 자격 검증과 저장 transaction을 소유한다(GitHub #125).
 *
 * <p>지역·방위·거리는 요청값이 아니라 잠근 {@link PostRecipient} 스냅샷에서 그대로
 * 가져온다. 수신 자격(ACTIVE·미삭제 질문글, 양방향 활성 차단, AVAILABLE/DISCOVERED/OPENED)
 * 검증과 답변 저장, 미디어 첨부, moderation job 접수를 한 transaction으로 묶어 부분
 * 반영을 막는다.</p>
 *
 * <p>moderation deadlineWindow 운영값이 아직 결정되지 않아 {@link AnswerModerationIntake}는
 * 현재 Spring bean으로 등록되지 않는다({@link com.dnd.qello.filtering.moderation.AnswerModerationJobIntakeService}
 * 참고). {@link ObjectProvider}로 부재를 감지해 fail-closed로 거절한다 — 값이 정해져
 * bean이 등록되면 이 클래스는 추가 변경 없이 활성화된다.</p>
 */
@Service
public class AnswerSubmissionService {

	private final AnswerRepository answerRepository;
	private final PostRecipientRepository postRecipientRepository;
	private final MediaAttachmentService mediaAttachmentService;
	private final ObjectProvider<AnswerModerationIntake> moderationIntakeProvider;
	private final TransactionTemplate transactionTemplate;

	public AnswerSubmissionService(
		AnswerRepository answerRepository,
		PostRecipientRepository postRecipientRepository,
		MediaAttachmentService mediaAttachmentService,
		ObjectProvider<AnswerModerationIntake> moderationIntakeProvider,
		PlatformTransactionManager transactionManager
	) {
		this.answerRepository = answerRepository;
		this.postRecipientRepository = postRecipientRepository;
		this.mediaAttachmentService = mediaAttachmentService;
		this.moderationIntakeProvider = moderationIntakeProvider;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public Answer submit(long authorId, String idempotencyKey, SubmitCommand command) {
		requireCommand(command);
		try {
			return transactionTemplate.execute(status -> submitInTransaction(authorId, idempotencyKey, command));
		} catch (DataIntegrityViolationException race) {
			return recoverConcurrentRequest(authorId, idempotencyKey, command, race);
		}
	}

	private Answer submitInTransaction(long authorId, String idempotencyKey, SubmitCommand command) {
		Optional<Answer> existing = answerRepository.findByAuthorAndIdempotencyKey(authorId, idempotencyKey);
		if (existing.isPresent()) {
			return validateReplay(existing.get(), command);
		}

		AnswerModerationIntake moderationIntake = moderationIntakeProvider.getIfAvailable();
		if (moderationIntake == null) {
			throw new AnswerException(AnswerErrorCode.MODERATION_INTAKE_NOT_CONFIGURED, "moderationIntake",
				"deadlineWindow 운영값이 결정되지 않아 답변 제출을 처리할 수 없습니다");
		}

		PostRecipient recipient = eligibleRecipient(authorId, command);
		Answer saved = answerRepository.save(Answer.submit(recipient.getId(), authorId, idempotencyKey,
			command.bodyText(), recipient.getMatchedRegionCode(), recipient.getMatchedBearingDegrees(),
			recipient.getDistanceBand(), command.submittedAt(), recipient.getDistanceM()));

		attachMedia(saved.getId(), authorId, command.mediaIds());

		moderationIntake.submit(FilterTarget.of(FilterTargetType.ANSWER, saved.getId()), command.bodyText(),
			ModerationLanguage.KO, moderationIdempotencyKey(saved.getId()));

		return saved;
	}

	private PostRecipient eligibleRecipient(long authorId, SubmitCommand command) {
		return postRecipientRepository
			.findInboxCommandItemForUpdate(command.postRecipientId(), authorId, command.submittedAt())
			.filter(AnswerSubmissionService::isOpenForAnswer)
			.orElseThrow(() -> new AnswerException(
				AnswerErrorCode.RECIPIENT_NOT_FOUND, "postRecipientId", "답변할 수 있는 수신 항목을 찾을 수 없습니다"));
	}

	// findInboxCommandItemForUpdate는 SKIP_PENDING도 반환한다(넘김 되돌리기 명령 공유 조건).
	// 답변 제출은 그 상태를 허용하지 않는다 — TASK.md 승인 범위가 AVAILABLE/DISCOVERED/OPENED로
	// 명시했다.
	private static boolean isOpenForAnswer(PostRecipient recipient) {
		return recipient.getStatus() == PostRecipientStatus.AVAILABLE
			|| recipient.getStatus() == PostRecipientStatus.DISCOVERED
			|| recipient.getStatus() == PostRecipientStatus.OPENED;
	}

	private Answer recoverConcurrentRequest(
		long authorId, String idempotencyKey, SubmitCommand command, DataIntegrityViolationException race
	) {
		Answer existing = answerRepository.findByAuthorAndIdempotencyKey(authorId, idempotencyKey)
			.orElseThrow(() -> race);
		return validateReplay(existing, command);
	}

	// 같은 (authorId, idempotencyKey)의 재생 요청만 여기 도달한다. postRecipientId·본문·미디어
	// 조합이 최초 요청과 다르면 같은 키의 다른 요청 재사용으로 거절한다.
	private Answer validateReplay(Answer existing, SubmitCommand command) {
		if (existing.getPostRecipientId() != command.postRecipientId()
			|| !Objects.equals(existing.getBodyText(), command.bodyText())) {
			throw idempotencyKeyReused();
		}
		List<Long> persistedMediaIds = mediaAttachmentService.findMediaIdsByAnswerId(existing.getId());
		if (!persistedMediaIds.equals(command.mediaIds())) {
			throw idempotencyKeyReused();
		}
		return existing;
	}

	private void attachMedia(long answerId, long authorId, List<Long> mediaIds) {
		for (int order = 0; order < mediaIds.size(); order++) {
			mediaAttachmentService.attach(new MediaAttachmentService.AttachCommand(
				authorId, mediaIds.get(order), null, answerId, order));
		}
	}

	private static String moderationIdempotencyKey(long answerId) {
		return "answer-moderation:" + answerId;
	}

	private static AnswerException idempotencyKeyReused() {
		return new AnswerException(AnswerErrorCode.IDEMPOTENCY_KEY_REUSED, "idempotencyKey",
			"같은 멱등키로 다른 요청을 재사용할 수 없습니다");
	}

	private static void requireCommand(SubmitCommand command) {
		if (command == null) {
			throw new AnswerException(AnswerErrorCode.REQUIRED_VALUE_MISSING, "command", "답변 제출 요청은 필수입니다");
		}
	}

	public record SubmitCommand(long postRecipientId, String bodyText, List<Long> mediaIds, Instant submittedAt) {
		public SubmitCommand {
			if (postRecipientId <= 0) {
				throw new AnswerException(AnswerErrorCode.INVALID_ID, "postRecipientId", "postRecipientId는 양수여야 합니다");
			}
			String normalizedBody = DirectionRequestFingerprint.normalizeBodyText(bodyText);
			if (normalizedBody == null) {
				throw new AnswerException(AnswerErrorCode.REQUIRED_VALUE_MISSING, "bodyText", "답변 본문은 필수입니다");
			}
			if (normalizedBody.codePoints().count() > DirectionPostProperties.APPROVED_MAX_BODY_CODE_POINTS) {
				throw new AnswerException(AnswerErrorCode.INVALID_TEXT, "bodyText", "본문이 허용 길이를 초과했습니다");
			}
			bodyText = normalizedBody;
			mediaIds = mediaIds == null ? List.of() : List.copyOf(mediaIds);
			if (mediaIds.size() > 1 || mediaIds.stream().anyMatch(id -> id == null || id <= 0)) {
				throw new AnswerException(AnswerErrorCode.INVALID_VALUE_RANGE, "mediaIds", "미디어 수 또는 ID가 유효하지 않습니다");
			}
			if (submittedAt == null) {
				throw new AnswerException(AnswerErrorCode.REQUIRED_VALUE_MISSING, "submittedAt", "submittedAt은 필수입니다");
			}
		}
	}
}
