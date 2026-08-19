/**
 * Created at: 2026-08-18T23:33:15+09:00
 * Source scenario: TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-014
 */
package com.dnd.qello.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.answer.config.MediaStorageProperties;
import com.dnd.qello.answer.domain.ImageMimeType;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;

class MediaStorageViewSettingsTest {

	private static final Duration UPLOAD_TTL = Duration.ofMinutes(10);
	private static final Duration VIEW_TTL = Duration.ofMinutes(5);
	private static final String DEFAULT_KEY = "media/defaults/profile-image.png";

	@Test
	@DisplayName("기본 프로필 이미지 키가 없으면 애플리케이션 시작 전에 거부한다")
	void rejectsMissingDefaultProfileImageKey() {
		for (String missing : new String[] {null, "", "   "}) {
			assertThatThrownBy(() -> properties(VIEW_TTL, missing))
				.isInstanceOf(AnswerException.class)
				.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.REQUIRED_VALUE_MISSING)
				.hasFieldOrPropertyWithValue("field", "defaultProfileImageKey");
		}
	}

	@Test
	@DisplayName("조회 URL TTL이 없거나 0 이하이면 애플리케이션 시작 전에 거부한다")
	void rejectsNonPositiveViewUrlTtl() {
		for (Duration invalid : new Duration[] {null, Duration.ZERO, Duration.ofMinutes(-1)}) {
			assertThatThrownBy(() -> properties(invalid, DEFAULT_KEY))
				.isInstanceOf(AnswerException.class)
				.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_METADATA)
				.hasFieldOrPropertyWithValue("field", "viewUrlTtl");
		}
	}

	@Test
	@DisplayName("조회 URL TTL은 업로드 TTL과 독립적으로 설정된다")
	void keepsViewTtlSeparateFromUploadTtl() {
		MediaStorageProperties properties = properties(VIEW_TTL, DEFAULT_KEY);

		assertThat(properties.viewUrlTtl()).isEqualTo(VIEW_TTL);
		assertThat(properties.uploadUrlTtl()).isEqualTo(UPLOAD_TTL);
		assertThat(properties.viewUrlTtl()).isNotEqualTo(properties.uploadUrlTtl());
	}

	private static MediaStorageProperties properties(Duration viewUrlTtl, String defaultProfileImageKey) {
		return new MediaStorageProperties("test-bucket", ImageMimeType.supportedMimeTypes(), 1_000L,
			UPLOAD_TTL, viewUrlTtl, defaultProfileImageKey);
	}
}
