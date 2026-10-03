package com.dnd.qello.feed.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.dnd.qello.answer.config.MediaStorageProperties;
import com.dnd.qello.answer.service.port.ObjectStoragePort;
import com.dnd.qello.answer.service.port.PresignedView;
import com.dnd.qello.feed.repository.AttachedMedia;
import com.dnd.qello.feed.repository.FeedMediaViewIssuer;
import com.dnd.qello.feed.view.MediaView;

import lombok.RequiredArgsConstructor;

/**
 * feed 카드의 첨부 미디어를 presigned GET URL로 바꾼다. 수명은 프로필 이미지와 같은 view-url-ttl을 쓴다.
 *
 * <p>
 * 발급 실패({@code STORAGE_UNAVAILABLE})는 그대로 전파해 목록 전체를 실패시킨다. 실패는 자격 증명 조회 단계에서 그
 * 시점의 발급 전체에 걸치므로, 실패한 항목만 빼면 모든 카드에서 이미지가 원인 없이 사라진다(TASK.md D2).
 */
@Component
@RequiredArgsConstructor
public class FeedMediaViewResolver implements FeedMediaViewIssuer {

	private final ObjectStoragePort objectStoragePort;
	private final MediaStorageProperties properties;

	@Override
	public List<MediaView> issue(List<AttachedMedia> media) {
		return media.stream().map(this::issue).toList();
	}

	private MediaView issue(AttachedMedia media) {
		PresignedView view = objectStoragePort.issueGetUrl(media.storageKey(), properties.viewUrlTtl());
		return new MediaView(media.mediaId(), view.url(), view.expiresAt());
	}
}
