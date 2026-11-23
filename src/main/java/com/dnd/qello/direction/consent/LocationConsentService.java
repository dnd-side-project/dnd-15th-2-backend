package com.dnd.qello.direction.consent;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountLockRepository;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.gate.LocationCollectionGate;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationConsentService {

	private final LocationConsentRepository repository;
	private final AccountRepository accountRepository;
	private final AccountLockRepository accountLockRepository;
	private final ActiveUserPresenceRepository presenceRepository;
	private final LocationConsentProperties properties;
	private final LocationCollectionGate locationCollectionGate;
	private final Clock clock;

	@PostConstruct
	void validateConfiguration() {
		properties.validate(locationCollectionGate.isEnabled());
	}

	@Transactional
	public CurrentConsent consent(long userId, String termsVersion) {
		requireActiveUser(accountLockRepository.findByIdForUpdate(userId));
		properties.validate(true);
		if (termsVersion == null || termsVersion.isBlank() || termsVersion.length() > 100
				|| !termsVersion.equals(termsVersion.strip())) {
			throw new DirectionException(DirectionErrorCode.INVALID_TEXT, "termsVersion", "약관 버전 형식이 올바르지 않습니다");
		}
		if (!properties.currentTermsVersion().equals(termsVersion)) {
			throw new DirectionException(DirectionErrorCode.LOCATION_CONSENT_VERSION_MISMATCH, "termsVersion",
					"현재 약관 버전으로 동의해야 합니다");
		}
		// 같은 계정의 최초 요청도 직렬화하며 잠금 뒤 새 statement로 최신 이력을 읽는다.
		repository.findLatestByUserId(userId);
		LocationConsent saved = repository.append(
				new LocationConsent(null, userId, termsVersion, LocationConsentAction.CONSENT, clock.instant()));
		return state(Optional.of(saved));
	}

	@Transactional
	public CurrentConsent withdraw(long userId) {
		requireActiveUser(accountLockRepository.findByIdForUpdate(userId));
		Optional<LocationConsent> latest = repository.findLatestByUserId(userId);
		String version = latest.map(LocationConsent::termsVersion).orElse(properties.currentTermsVersion());
		if (version == null) {
			properties.validate(true);
		}
		LocationConsent saved = repository.append(
				new LocationConsent(null, userId, version, LocationConsentAction.WITHDRAW, clock.instant()));
		presenceRepository.deleteByUserId(userId);
		return state(Optional.of(saved));
	}

	public CurrentConsent current(long userId) {
		requireActiveUser(accountRepository.findById(userId));
		return state(repository.findLatestByUserId(userId));
	}

	public void ensureConsented(long userId) {
		if (repository.findLatestByUserId(userId)
				.filter(consent -> consent.isConsentedTo(properties.currentTermsVersion())).isEmpty()) {
			throw new DirectionException(DirectionErrorCode.LOCATION_CONSENT_REQUIRED, null,
					"현재 약관에 대한 위치정보 이용 동의가 필요합니다");
		}
	}

	private CurrentConsent state(Optional<LocationConsent> latest) {
		return new CurrentConsent(properties.currentTermsVersion(),
				latest.map(consent -> consent.isConsentedTo(properties.currentTermsVersion())).orElse(false),
				latest.map(LocationConsent::action).orElse(null),
				latest.map(LocationConsent::termsVersion).orElse(null),
				latest.map(LocationConsent::recordedAt).orElse(null));
	}

	private void requireActiveUser(Optional<Account> found) {
		Account account = found.orElseThrow(() -> new DirectionException(
				DirectionErrorCode.PRESENCE_ACCOUNT_NOT_FOUND, "userId", "계정을 찾을 수 없습니다"));
		if (account.getRole() != AccountRole.USER || account.getStatus() != AccountStatus.ACTIVE) {
			throw new DirectionException(DirectionErrorCode.PRESENCE_ACCOUNT_NOT_ELIGIBLE, "userId",
					"현재 계정은 위치정보 동의 기능을 사용할 수 없습니다");
		}
	}

	public record CurrentConsent(String currentTermsVersion, boolean consented, LocationConsentAction lastAction,
			String lastTermsVersion, Instant recordedAt) {
	}
}
