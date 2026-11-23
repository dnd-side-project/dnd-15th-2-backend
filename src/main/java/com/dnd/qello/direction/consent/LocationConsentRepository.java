package com.dnd.qello.direction.consent;

import java.util.Optional;

public interface LocationConsentRepository {

	LocationConsent append(LocationConsent consent);

	/** 버전이나 시각으로 거르지 않고 사용자 전체에서 id가 가장 큰 행을 조회한다. */
	Optional<LocationConsent> findLatestByUserId(long userId);
}
