package com.dnd.qello.filtering.repository.jpa;

import java.time.Instant;

import com.dnd.qello.filtering.domain.AppealAcceptanceReasonCode;
import com.dnd.qello.filtering.domain.AppealCase;
import com.dnd.qello.filtering.domain.AppealCaseStatus;
import com.dnd.qello.filtering.domain.AppealDecision;
import com.dnd.qello.filtering.domain.FilterTargetType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "appeal_case")
public class AppealCaseJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, length = 20)
	private FilterTargetType targetType;
	@Column(name = "target_id", nullable = false)
	private long targetId;
	@Column(name = "filter_decision_id", nullable = false)
	private long filterDecisionId;
	@Column(name = "appellant_user_id", nullable = false)
	private long appellantUserId;
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private AppealCaseStatus status;
	@Column(name = "window_started_at", nullable = false)
	private Instant windowStartedAt;
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;
	@Enumerated(EnumType.STRING)
	@Column(name = "acceptance_reason_code", nullable = false, length = 30)
	private AppealAcceptanceReasonCode acceptanceReasonCode;
	@Enumerated(EnumType.STRING)
	@Column(name = "decision", length = 20)
	private AppealDecision decision;
	@Column(name = "decided_at")
	private Instant decidedAt;
	@Column(name = "decided_by_operator_user_id")
	private Long decidedByOperatorUserId;
	@Column(name = "restore_blocked_reason_code", length = 30)
	private String restoreBlockedReasonCode;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AppealCaseJpaEntity() { }

	AppealCaseJpaEntity(AppealCase appealCase) {
		this.id = appealCase.id();
		this.targetType = appealCase.targetType();
		this.targetId = appealCase.targetId();
		this.filterDecisionId = appealCase.filterDecisionId();
		this.appellantUserId = appealCase.appellantUserId();
		this.status = appealCase.status();
		this.windowStartedAt = appealCase.windowStartedAt();
		this.expiresAt = appealCase.expiresAt();
		this.acceptanceReasonCode = appealCase.acceptanceReasonCode();
		this.decision = appealCase.decision();
		this.decidedAt = appealCase.decidedAt();
		this.decidedByOperatorUserId = appealCase.decidedByOperatorUserId();
		this.restoreBlockedReasonCode = appealCase.restoreBlockedReasonCode();
		this.createdAt = appealCase.createdAt();
	}

	Long getId() { return id; }
	FilterTargetType getTargetType() { return targetType; }
	long getTargetId() { return targetId; }
	long getFilterDecisionId() { return filterDecisionId; }
	long getAppellantUserId() { return appellantUserId; }
	AppealCaseStatus getStatus() { return status; }
	Instant getWindowStartedAt() { return windowStartedAt; }
	Instant getExpiresAt() { return expiresAt; }
	AppealAcceptanceReasonCode getAcceptanceReasonCode() { return acceptanceReasonCode; }
	AppealDecision getDecision() { return decision; }
	Instant getDecidedAt() { return decidedAt; }
	Long getDecidedByOperatorUserId() { return decidedByOperatorUserId; }
	String getRestoreBlockedReasonCode() { return restoreBlockedReasonCode; }
	Instant getCreatedAt() { return createdAt; }
}
