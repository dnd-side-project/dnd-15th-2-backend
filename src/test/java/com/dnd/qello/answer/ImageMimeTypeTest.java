/**
 * Created at: 2026-08-14T18:20:00+09:00
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-004
 */
package com.dnd.qello.answer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.answer.domain.ImageMimeType;

class ImageMimeTypeTest {

	@Test
	@DisplayName("이미지 포맷은 canonical MIME과 허용 별칭을 한 곳에서 제공한다")
	void exposesCanonicalMimeAndAliases() {
		assertThat(ImageMimeType.supportedMimeTypes())
			.containsExactlyInAnyOrder("image/jpeg", "image/png");
		assertThat(ImageMimeType.canonicalMimeType(" IMAGE/JPG "))
			.isEqualTo(ImageMimeType.JPEG.mimeType());
		assertThat(ImageMimeType.canonicalMimeType("image/webp")).isNull();
	}
}
