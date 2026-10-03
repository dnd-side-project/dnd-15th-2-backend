package com.dnd.qello.feed.web.response;

import java.time.Instant;
import java.util.List;

import com.dnd.qello.feed.view.MediaView;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * feed 카드에 첨부된 이미지 1건. 버킷 이름과 storage key는 담지 않는다 — 클라이언트에 필요한 것은 만료가 있는 조회
 * URL뿐이다.
 */
@Schema(name = "FeedMedia", description = "첨부 이미지와 일정 시간이 지나면 만료되는 조회 URL")
public record MediaResponse(
		@Schema(description = "첨부 이미지 식별자. 같은 이미지는 조회할 때마다 URL이 바뀌므로 클라이언트 캐시 키로는 이 값을 씁니다") long mediaId,
		@Schema(description = "이미지 조회 URL. expiresAt이 지나면 403을 받으므로 목록을 다시 조회해 새 URL을 받습니다") String url,
		@Schema(description = "조회 URL이 만료되는 시각") Instant expiresAt) {

	public static List<MediaResponse> from(List<MediaView> media) {
		return media.stream()
				.map(view -> new MediaResponse(view.mediaId(), view.url().toString(), view.expiresAt()))
				.toList();
	}
}
