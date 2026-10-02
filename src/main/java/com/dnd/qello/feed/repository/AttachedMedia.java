package com.dnd.qello.feed.repository;

/**
 * 카드에 첨부된 READY 미디어 1건의 저장 위치. storage key는 버킷 내부 경로라 응답으로 내보내지 않는다 —
 * {@link FeedMediaViewIssuer}가 조회 URL로 바꾼 뒤에야 view에 들어간다.
 */
public record AttachedMedia(long mediaId, String storageKey) {
}
