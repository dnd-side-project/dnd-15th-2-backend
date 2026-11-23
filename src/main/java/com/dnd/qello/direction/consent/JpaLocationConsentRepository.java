package com.dnd.qello.direction.consent;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaLocationConsentRepository implements LocationConsentRepository {

	private final SpringDataLocationConsentRepository repository;

	@Override
	@Transactional
	public LocationConsent append(LocationConsent consent) {
		if (consent.id() != null) {
			throw new DirectionException(DirectionErrorCode.INVALID_ID, "id", "새 동의 이력에는 식별자가 없어야 합니다");
		}
		return repository.save(new LocationConsentEntity(consent)).toDomain();
	}

	@Override
	public Optional<LocationConsent> findLatestByUserId(long userId) {
		return repository.findFirstByUserIdOrderByIdDesc(userId).map(LocationConsentEntity::toDomain);
	}
}
