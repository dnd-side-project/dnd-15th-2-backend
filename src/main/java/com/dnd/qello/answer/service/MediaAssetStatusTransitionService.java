package com.dnd.qello.answer.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.answer.domain.MediaAsset;
import com.dnd.qello.answer.repository.MediaAssetRepository;

import lombok.RequiredArgsConstructor;

/**
 * 미디어 상태 전이를 위한 짧은 트랜잭션을 담당한다.
 *
 * <p>업로드 완료 확인은 S3 {@code HeadObject}라는 외부 I/O를 포함하므로
 * {@link MediaUploadService}의 확인 흐름 전체를 하나의 트랜잭션으로 묶지 않는다.
 * 이 서비스를 별도 Spring bean으로 두어 호출이 프록시를 통과하게 하고, 같은 클래스
 * 내부 호출로 인해 {@code @Transactional} 옵션이 무시되는 위험 없이 조건부 상태 전이
 * UPDATE만 짧은 트랜잭션에서 실행한다.</p>
 */
@Service
@RequiredArgsConstructor
public class MediaAssetStatusTransitionService {

	private final MediaAssetRepository mediaAssetRepository;

	@Transactional
	public Optional<MediaAsset> transitionFromUploading(MediaAsset next) {
		return mediaAssetRepository.transitionFromUploading(next);
	}
}
