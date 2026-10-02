package com.dnd.qello.feed.repository;

import java.util.List;

import com.dnd.qello.feed.view.MediaView;

/**
 * 첨부 미디어의 storage key를 클라이언트가 바로 받을 수 있는 조회 URL로 바꾼다. 조회 repository가 카드를 만들 때
 * 호출해, storage key가 repository 밖으로 나가지 않게 한다. 입력 순서를 그대로 유지한다.
 */
public interface FeedMediaViewIssuer {

	List<MediaView> issue(List<AttachedMedia> media);
}
