package com.dnd.qello.feed.view;

import java.util.List;

/**
 * `내게 온 질문` 목록과 방향 칩을 함께 담는다.
 * chips는 cards에 걸린 방향 필터와 무관하게 카테고리 전체를 집계한다 — 필터를
 * 건 상태에서도 다른 방향으로 갈아탈 수 있어야 하기 때문이다.
 */
public record InboxListing(List<InboxCard> cards, List<DirectionChip> chips) {

	public InboxListing {
		cards = List.copyOf(cards);
		chips = List.copyOf(chips);
	}
}
