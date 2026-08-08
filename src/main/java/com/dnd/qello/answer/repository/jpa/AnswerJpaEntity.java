package com.dnd.qello.answer.repository.jpa;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.DynamicUpdate;

import com.dnd.qello.answer.domain.AnswerModerationStatus;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.domain.Answer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "answer")
@DynamicUpdate
public class AnswerJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "post_recipient_id", nullable = false)
	private Long postRecipientId;
	@Column(name = "author_id", nullable = false)
	private Long authorId;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AnswerStatus status;
	@Column(name = "idempotency_key", nullable = false, length = 200)
	private String idempotencyKey;
	@Column(name = "body_text", columnDefinition = "TEXT")
	private String bodyText;
	@Column(name = "coarse_region_code", nullable = false, length = 100)
	private String coarseRegionCode;
	@Column(name = "bearing_from_sender_deg", nullable = false, precision = 7, scale = 3)
	private BigDecimal bearingFromSenderDegrees;
	@Column(name = "distance_band", nullable = false, length = 50)
	private String distanceBand;
	@Enumerated(EnumType.STRING)
	@Column(name = "moderation_status", nullable = false, length = 30)
	private AnswerModerationStatus moderationStatus;
	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;
	@Column(name = "published_at")
	private Instant publishedAt;
	@Column(name = "deleted_at")
	private Instant deletedAt;
	@Column(name = "distance_m", nullable = false)
	private long distanceM;
	@Column(name = "edited_at")
	private Instant editedAt;
	@Column(name = "edit_count", nullable = false)
	private int editCount;

	protected AnswerJpaEntity() { }

	AnswerJpaEntity(Answer answer) {
		this.id = answer.getId();
		this.postRecipientId = answer.getPostRecipientId();
		this.authorId = answer.getAuthorId();
		this.status = answer.getStatus();
		this.idempotencyKey = answer.getIdempotencyKey();
		this.bodyText = answer.getBodyText();
		this.coarseRegionCode = answer.getCoarseRegionCode();
		this.bearingFromSenderDegrees = answer.getBearingFromSenderDegrees();
		this.distanceBand = answer.getDistanceBand();
		this.moderationStatus = answer.getModerationStatus();
		this.submittedAt = answer.getSubmittedAt();
		this.publishedAt = answer.getPublishedAt();
		this.deletedAt = answer.getDeletedAt();
		this.distanceM = answer.getDistanceM();
		this.editedAt = answer.getEditedAt();
		this.editCount = answer.getEditCount();
	}

	Long getId() { return id; }
	Long getPostRecipientId() { return postRecipientId; }
	Long getAuthorId() { return authorId; }
	AnswerStatus getStatus() { return status; }
	String getIdempotencyKey() { return idempotencyKey; }
	String getBodyText() { return bodyText; }
	String getCoarseRegionCode() { return coarseRegionCode; }
	BigDecimal getBearingFromSenderDegrees() { return bearingFromSenderDegrees; }
	String getDistanceBand() { return distanceBand; }
	AnswerModerationStatus getModerationStatus() { return moderationStatus; }
	Instant getSubmittedAt() { return submittedAt; }
	Instant getPublishedAt() { return publishedAt; }
	Instant getDeletedAt() { return deletedAt; }
	long getDistanceM() { return distanceM; }
	Instant getEditedAt() { return editedAt; }
	int getEditCount() { return editCount; }
}
