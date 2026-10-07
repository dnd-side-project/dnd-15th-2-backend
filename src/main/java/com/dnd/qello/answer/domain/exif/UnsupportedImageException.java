package com.dnd.qello.answer.domain.exif;

/**
 * 메타데이터 경계를 확정할 수 없을 만큼 구조가 깨진 이미지. 메시지에는 입력 바이트를 넣지 않는다.
 */
public class UnsupportedImageException extends RuntimeException {

	UnsupportedImageException(String message) {
		super(message);
	}
}
