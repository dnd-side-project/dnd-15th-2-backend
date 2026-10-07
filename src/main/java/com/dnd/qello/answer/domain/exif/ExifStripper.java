package com.dnd.qello.answer.domain.exif;

import com.dnd.qello.answer.domain.ImageMimeType;

/**
 * 이미지에서 위치정보가 들어갈 수 있는 메타데이터를 무손실로 지운다.
 *
 * <p>
 * 픽셀 데이터는 다시 인코딩하지 않고 그대로 복사한다. 지울 것을 고르지 않고 남길 것만 고르는 허용 목록 방식이라, 모르는 세그먼트·청크에
 * 위치정보가 들어 있어도 함께 사라진다. 남기는 메타데이터는 화면 표시에 필요한 회전(Orientation)과 색 정보뿐이다.
 *
 * <p>
 * S3와 Spring에 의존하지 않는다. 처리 위치를 API 서버 밖으로 옮겨도 이 클래스를 그대로 쓴다(#325).
 */
public final class ExifStripper {

	private ExifStripper() {
	}

	/**
	 * @throws UnsupportedImageException
	 *             구조가 깨져 메타데이터 경계를 확정할 수 없는 이미지
	 */
	public static byte[] strip(ImageMimeType format, byte[] image) {
		if (format == null || image == null) {
			throw new IllegalArgumentException("format과 image는 필수입니다");
		}
		return switch (format) {
			case JPEG -> JpegMetadataStripper.strip(image);
			case PNG -> PngMetadataStripper.strip(image);
		};
	}
}
