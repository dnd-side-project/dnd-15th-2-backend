package com.dnd.qello.direction.consent;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Immutable
@Table(name = "location_consent_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationConsentEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private long userId;

	@Column(name = "terms_version", nullable = false, length = 100, updatable = false)
	private String termsVersion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16, updatable = false)
	private LocationConsentAction action;

	@Column(name = "recorded_at", nullable = false, updatable = false)
	private Instant recordedAt;

	LocationConsentEntity(LocationConsent consent) {
		this.userId = consent.userId();
		this.termsVersion = consent.termsVersion();
		this.action = consent.action();
		this.recordedAt = consent.recordedAt();
	}

	LocationConsent toDomain() {
		return new LocationConsent(id, userId, termsVersion, action, recordedAt);
	}
}
