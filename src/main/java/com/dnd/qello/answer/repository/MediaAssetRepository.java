package com.dnd.qello.answer.repository;

import java.util.Optional;

import com.dnd.qello.answer.domain.MediaAsset;

public interface MediaAssetRepository {

	MediaAsset save(MediaAsset asset);

	/**
	 * 소유권 검증이 없는 조회다. 이미 다른 경로로 자격을 확인한 내부 서버 로직에서만 쓴다.
	 * 사용자 입력으로 직접 이 메서드를 호출하지 않는다. 그런 경로는 findByIdAndOwnerId를 쓴다.
	 */
	Optional<MediaAsset> findById(long id);

	/** 소유권을 쿼리 조건에 포함한다. 남의 자산이면 빈 결과이며 예외를 던지지 않는다. */
	Optional<MediaAsset> findByIdAndOwnerId(long id, long ownerId);

	/**
	 * status가 여전히 UPLOADING일 때만 next의 상태로 전이하고 저장한다. 영향받은 행이 없으면
	 * (이미 다른 트랜잭션이 먼저 확정한 경우) empty를 반환한다 — 호출자는 이 empty를 신호로
	 * 현재 상태를 다시 조회해 멱등하게 처리하고, 상태를 두 번 확정하지 않는다. 외부 저장소
	 * 확인과 분리된 짧은 트랜잭션에서 실행한다.
	 */
	Optional<MediaAsset> transitionFromUploading(MediaAsset next);
}
