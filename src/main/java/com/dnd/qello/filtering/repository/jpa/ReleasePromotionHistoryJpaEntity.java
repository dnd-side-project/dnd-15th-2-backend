package com.dnd.qello.filtering.repository.jpa;

import java.time.Instant;

import com.dnd.qello.filtering.domain.ReleasePromotionAction;
import com.dnd.qello.filtering.domain.ReleasePromotionHistoryEntry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "release_promotion_history")
public class ReleasePromotionHistoryJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "release_id", nullable = false)
	private long releaseId;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReleasePromotionAction action;
	@Column(name = "previous_active_release_id")
	private Long previousActiveReleaseId;
	@Column(name = "operator_user_id", nullable = false)
	private long operatorUserId;
	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	protected ReleasePromotionHistoryJpaEntity() { }

	ReleasePromotionHistoryJpaEntity(ReleasePromotionHistoryEntry entry) {
		this.id = entry.id();
		this.releaseId = entry.releaseId();
		this.action = entry.action();
		this.previousActiveReleaseId = entry.previousActiveReleaseId();
		this.operatorUserId = entry.operatorUserId();
		this.occurredAt = entry.occurredAt();
	}

	Long getId() { return id; }
	long getReleaseId() { return releaseId; }
	ReleasePromotionAction getAction() { return action; }
	Long getPreviousActiveReleaseId() { return previousActiveReleaseId; }
	long getOperatorUserId() { return operatorUserId; }
	Instant getOccurredAt() { return occurredAt; }
}
