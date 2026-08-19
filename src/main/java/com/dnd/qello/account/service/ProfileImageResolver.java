package com.dnd.qello.account.service;

import java.net.URL;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.answer.config.MediaStorageProperties;
import com.dnd.qello.answer.domain.MediaAsset;
import com.dnd.qello.answer.domain.MediaAssetStatus;
import com.dnd.qello.answer.repository.MediaAssetRepository;
import com.dnd.qello.answer.service.port.ObjectStoragePort;
import com.dnd.qello.answer.service.port.PresignedView;

import lombok.RequiredArgsConstructor;

/**
 * 계정의 프로필 이미지 참조를 실제 조회 URL로 바꾼다.
 *
 * <p>참조가 없거나 참조한 자산이 더 이상 READY가 아니면 기본 이미지로 폴백한다. 폴백은 읽는
 * 쪽의 해석으로만 처리하고 프로필 참조를 지우지 않는다 — 조회가 계정 행을 쓰면 단순 조회가
 * 낙관적 락 충돌로 실패할 수 있고, 자산이 되살아나는 경로가 생겼을 때 원래 참조를 잃는다.
 *
 * <p>이 폴백은 설정 시점의 검증을 완화하지 않는다. READY가 아닌 자산을 프로필로 새로
 * 지정하는 것은 {@link ProfileService}가 여전히 거부한다.
 */
@Component
@RequiredArgsConstructor
public class ProfileImageResolver {

	private final MediaAssetRepository mediaAssetRepository;
	private final ObjectStoragePort objectStoragePort;
	private final MediaStorageProperties properties;

	public ResolvedProfileImage resolve(Account account) {
		String ownKey = ownStorageKey(account);
		boolean usesDefault = ownKey == null;
		String storageKey = usesDefault ? properties.defaultProfileImageKey() : ownKey;
		PresignedView view = objectStoragePort.issueGetUrl(storageKey, properties.viewUrlTtl());
		return new ResolvedProfileImage(view.url(), view.expiresAt(), usesDefault);
	}

	/** 프로필로 쓸 수 있는 자신의 자산이 있으면 그 storage key를, 없으면 null을 반환한다. */
	private String ownStorageKey(Account account) {
		Long mediaId = account.getProfileImageMediaId();
		if (mediaId == null) {
			return null;
		}
		return mediaAssetRepository.findByIdAndOwnerId(mediaId, account.getId())
			.filter(asset -> asset.getStatus() == MediaAssetStatus.READY)
			.map(MediaAsset::getStorageKey)
			.orElse(null);
	}

	/**
	 * 조회 URL과 그것이 기본 이미지인지 여부. storage key와 버킷 이름은 담지 않는다 — 이
	 * 값이 그대로 API 응답으로 나가기 때문이다.
	 */
	public record ResolvedProfileImage(URL url, Instant expiresAt, boolean usesDefaultImage) {
	}
}
