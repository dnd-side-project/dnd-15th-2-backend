/**
 * Created at: 2026-10-02T17:03:19+09:00
 * Source scenario: TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-UNIT-001 through UNIT-003
 */
package com.dnd.qello.feed.service;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.answer.config.MediaStorageProperties;
import com.dnd.qello.answer.domain.ImageMimeType;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.service.port.ObjectStoragePort;
import com.dnd.qello.answer.service.port.PresignedUpload;
import com.dnd.qello.answer.service.port.PresignedView;
import com.dnd.qello.answer.service.port.StoredObjectMetadata;
import com.dnd.qello.feed.repository.AttachedMedia;
import com.dnd.qello.feed.view.MediaView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedMediaViewResolverTest {

	private static final Instant ISSUED_AT = Instant.parse("2026-10-02T00:00:00Z");
	private static final Duration VIEW_TTL = Duration.ofMinutes(5);

	private final MediaStorageProperties properties = new MediaStorageProperties("test-bucket",
			ImageMimeType.supportedMimeTypes(), 1_000L, Duration.ofMinutes(10), VIEW_TTL, "media/defaults/profile.png");
	private final RecordingObjectStoragePort storage = new RecordingObjectStoragePort();
	private final FeedMediaViewResolver resolver = new FeedMediaViewResolver(storage, properties);

	@Test
	@DisplayName("첨부가 없으면 빈 목록을 반환하고 조회 URL을 발급하지 않는다")
	void returnsEmptyWithoutIssuingWhenNoMedia() {
		assertThat(resolver.issue(List.of())).isEmpty();

		assertThat(storage.requestedKeys).isEmpty();
	}

	@Test
	@DisplayName("입력 순서와 mediaId를 유지하고 key별로 view-url-ttl 수명의 조회 URL을 발급한다")
	void issuesViewUrlPerMediaInInputOrder() {
		List<MediaView> views = resolver.issue(
				List.of(new AttachedMedia(11L, "media/7/key-a"), new AttachedMedia(12L, "media/7/key-b")));

		assertThat(views).extracting(MediaView::mediaId).containsExactly(11L, 12L);
		assertThat(views).extracting(view -> view.url().toString()).containsExactly(
				"https://example-test.invalid/media/7/key-a?signed",
				"https://example-test.invalid/media/7/key-b?signed");
		assertThat(views).extracting(MediaView::expiresAt).containsOnly(ISSUED_AT.plus(VIEW_TTL));
		assertThat(storage.requestedKeys).containsExactly("media/7/key-a", "media/7/key-b");
		assertThat(storage.requestedTtls).containsOnly(VIEW_TTL);
	}

	@Test
	@DisplayName("조회 URL 발급이 실패하면 STORAGE_UNAVAILABLE을 그대로 전파하고 일부만 담긴 결과를 반환하지 않는다")
	void propagatesStorageUnavailable() {
		storage.failOnKey = "media/7/key-b";

		assertThatThrownBy(() -> resolver.issue(
				List.of(new AttachedMedia(11L, "media/7/key-a"), new AttachedMedia(12L, "media/7/key-b"))))
				.isInstanceOf(AnswerException.class)
				.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.STORAGE_UNAVAILABLE);
	}

	private static final class RecordingObjectStoragePort implements ObjectStoragePort {
		private final List<String> requestedKeys = new ArrayList<>();
		private final List<Duration> requestedTtls = new ArrayList<>();
		private String failOnKey;

		@Override
		public PresignedUpload issuePutUrl(String storageKey, String contentType, Duration ttl) {
			throw new UnsupportedOperationException();
		}

		@Override
		public PresignedView issueGetUrl(String storageKey, Duration ttl) {
			if (storageKey.equals(failOnKey)) {
				throw new AnswerException(AnswerErrorCode.STORAGE_UNAVAILABLE, null, "조회 URL 발급에 실패했습니다");
			}
			requestedKeys.add(storageKey);
			requestedTtls.add(ttl);
			return new PresignedView(url(storageKey), ISSUED_AT.plus(ttl));
		}

		@Override
		public Optional<StoredObjectMetadata> headObject(String storageKey) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<byte[]> readObjectPrefix(String storageKey, int maxBytes) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void putObject(String storageKey, String contentType, byte[] body) {
			throw new UnsupportedOperationException();
		}

		private static URL url(String storageKey) {
			try {
				return URI.create("https://example-test.invalid/" + storageKey + "?signed").toURL();
			} catch (MalformedURLException exception) {
				throw new IllegalStateException(exception);
			}
		}
	}
}
