package com.dnd.qello.answer.domain;

import java.util.UUID;

/**
 * 업로드 원본과 서빙용 처리본의 객체 key 규칙.
 *
 * <p>
 * presigned PUT URL은 수명 동안 다시 쓸 수 있다. 처리본을 원본과 같은 key에 두면 READY가 된 뒤 원본을 다시 올려
 * 처리본을 덮어쓸 수 있으므로, 처리본은 항상 다른 key에 둔다. 원본은 {@code uploads/}에만 있으므로 나중에 이
 * prefix에만 짧은 수명주기를 걸 수 있다.
 */
public final class MediaStorageKeys {

	private static final String UPLOAD_PREFIX = "uploads/";
	private static final String SERVING_PREFIX = "media/";
	// 이 규칙 전에 발급된 upload key는 media/로 시작한다. prefix만 바꾸면 원본 key와 같아지므로 접미사로 구분한다.
	private static final String LEGACY_SERVING_SUFFIX = "/stripped";

	private MediaStorageKeys() {
	}

	public static String uploadKey(long ownerId, UUID objectId) {
		return UPLOAD_PREFIX + ownerId + "/" + objectId;
	}

	/** 같은 upload key에는 항상 같은 serving key를 돌려준다. 동시 confirm이 같은 객체를 쓰게 하기 위해서다. */
	public static String servingKeyOf(String uploadKey) {
		if (uploadKey.startsWith(UPLOAD_PREFIX)) {
			return SERVING_PREFIX + uploadKey.substring(UPLOAD_PREFIX.length());
		}
		return uploadKey + LEGACY_SERVING_SUFFIX;
	}
}
