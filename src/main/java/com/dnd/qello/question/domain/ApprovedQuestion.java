package com.dnd.qello.question.domain;

import java.time.Instant;

import com.dnd.qello.question.error.QuestionErrorCode;
import com.dnd.qello.question.error.QuestionException;

public final class ApprovedQuestion {

	private static final int TEXT_MAX_LENGTH = 2_000;

	private final Long id;
	private final Long sourceProposalId;
	private final ApprovedQuestionSourceType sourceType;
	private final ApprovedQuestionStatus status;
	private final String questionText;
	private final AnswerFormat answerFormat;
	private final Instant activeFrom;
	private final Instant activeUntil;
	private final Instant approvedAt;
	private final Long approvedBy;
	private final Instant createdAt;

	private ApprovedQuestion(
		Long id, Long sourceProposalId, ApprovedQuestionSourceType sourceType,
		ApprovedQuestionStatus status, String questionText, AnswerFormat answerFormat,
		Instant activeFrom, Instant activeUntil, Instant approvedAt, Long approvedBy,
		Instant createdAt
	) {
		this.id = validateId(id, "id");
		this.sourceProposalId = validateId(sourceProposalId, "sourceProposalId");
		this.sourceType = requireValue(sourceType, "sourceType");
		this.status = requireValue(status, "status");
		this.questionText = requireText(questionText, "questionText");
		this.answerFormat = requireValue(answerFormat, "answerFormat");
		this.activeFrom = activeFrom;
		this.activeUntil = activeUntil;
		this.approvedAt = approvedAt;
		this.approvedBy = validateId(approvedBy, "approvedBy");
		this.createdAt = createdAt;
		validateSource();
		validateRange();
		if (status == ApprovedQuestionStatus.ACTIVE
			&& (approvedAt == null || approvedBy == null || activeFrom == null)) {
			throw new QuestionException(
				QuestionErrorCode.INVALID_QUESTION_STATE, "status", "ACTIVE 질문에는 승인 정보와 activeFrom이 필요합니다");
		}
	}

	public static ApprovedQuestion fromUserProposalPending(
		Long sourceProposalId, String questionText, AnswerFormat answerFormat
	) {
		return new ApprovedQuestion(null, sourceProposalId, ApprovedQuestionSourceType.USER_PROPOSAL,
			ApprovedQuestionStatus.PENDING_REVIEW, questionText, answerFormat,
			null, null, null, null, null);
	}

	public static ApprovedQuestion activeUserProposal(
		Long sourceProposalId, String questionText, AnswerFormat answerFormat,
		Instant activeFrom, Instant activeUntil, Instant approvedAt, Long approvedBy
	) {
		return new ApprovedQuestion(null, sourceProposalId, ApprovedQuestionSourceType.USER_PROPOSAL,
			ApprovedQuestionStatus.ACTIVE, questionText, answerFormat,
			activeFrom, activeUntil, approvedAt, approvedBy, null);
	}

	public static ApprovedQuestion activeOperatorQuestion(
		String questionText, AnswerFormat answerFormat, Instant activeFrom,
		Instant activeUntil, Instant approvedAt, Long approvedBy
	) {
		return new ApprovedQuestion(null, null, ApprovedQuestionSourceType.OPERATOR,
			ApprovedQuestionStatus.ACTIVE, questionText, answerFormat,
			activeFrom, activeUntil, approvedAt, approvedBy, null);
	}

	public static ApprovedQuestion restore(
		Long id, Long sourceProposalId, ApprovedQuestionSourceType sourceType,
		ApprovedQuestionStatus status, String questionText, AnswerFormat answerFormat,
		Instant activeFrom, Instant activeUntil, Instant approvedAt, Long approvedBy,
		Instant createdAt
	) {
		return new ApprovedQuestion(id, sourceProposalId, sourceType, status, questionText,
			answerFormat, activeFrom, activeUntil, approvedAt, approvedBy, createdAt);
	}

	public boolean isAssignableAt(Instant at) {
		requireValue(at, "at");
		return status == ApprovedQuestionStatus.ACTIVE
			&& activeFrom != null && !at.isBefore(activeFrom)
			&& (activeUntil == null || at.isBefore(activeUntil));
	}

	private void validateSource() {
		boolean userProposal = sourceType == ApprovedQuestionSourceType.USER_PROPOSAL;
		if (userProposal != (sourceProposalId != null)) {
			throw new QuestionException(
				QuestionErrorCode.INVALID_QUESTION_STATE,
				"sourceProposalId",
				"USER_PROPOSAL만 sourceProposalId를 가질 수 있습니다"
			);
		}
	}

	private void validateRange() {
		if (activeUntil != null && activeFrom != null && !activeUntil.isAfter(activeFrom)) {
			throw new QuestionException(
				QuestionErrorCode.INVALID_TIME_ORDER, "activeUntil", "activeUntil은 activeFrom보다 늦어야 합니다");
		}
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new QuestionException(QuestionErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}

	private static Long validateId(Long value, String field) {
		if (value != null && value <= 0) {
			throw new QuestionException(QuestionErrorCode.INVALID_ID, field, field + "는 양수여야 합니다");
		}
		return value;
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new QuestionException(
				QuestionErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 비어 있을 수 없습니다");
		}
		if (value.length() > TEXT_MAX_LENGTH) {
			throw new QuestionException(
				QuestionErrorCode.TEXT_TOO_LONG, field, field + "은 " + TEXT_MAX_LENGTH + "자를 초과할 수 없습니다");
		}
		return value;
	}

	public Long getId() { return id; }
	public Long getSourceProposalId() { return sourceProposalId; }
	public ApprovedQuestionSourceType getSourceType() { return sourceType; }
	public ApprovedQuestionStatus getStatus() { return status; }
	public String getQuestionText() { return questionText; }
	public AnswerFormat getAnswerFormat() { return answerFormat; }
	public Instant getActiveFrom() { return activeFrom; }
	public Instant getActiveUntil() { return activeUntil; }
	public Instant getApprovedAt() { return approvedAt; }
	public Long getApprovedBy() { return approvedBy; }
	public Instant getCreatedAt() { return createdAt; }
}
