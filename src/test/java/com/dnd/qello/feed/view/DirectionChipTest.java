/**
 * Created at: 2026-08-08T21:30:12+09:00
 * Source scenario: TEST-PLAN-GH-80-INBOX-DIRECTION-CHIPS-UNIT-001, UNIT-002, UNIT-003
 */
package com.dnd.qello.feed.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;

class DirectionChipTest {

	@Test
	@DisplayName("segmentKey가 null이거나 공백이면 생성이 거부된다")
	void rejectsBlankSegmentKey() {
		assertThatThrownBy(() -> new DirectionChip(null, "북", 0, 1L))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INVALID_TEXT);
		assertThatThrownBy(() -> new DirectionChip("  ", "북", 0, 1L))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INVALID_TEXT);
	}

	@Test
	@DisplayName("displayName이 null이거나 공백이면 생성이 거부된다")
	void rejectsBlankDisplayName() {
		assertThatThrownBy(() -> new DirectionChip("N", null, 0, 1L))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INVALID_TEXT);
		assertThatThrownBy(() -> new DirectionChip("N", " ", 0, 1L))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INVALID_TEXT);
	}

	@Test
	@DisplayName("count와 sortOrder가 음수이면 생성이 거부되고, count 0은 허용된다")
	void rejectsNegativeCountAndSortOrder() {
		assertThatThrownBy(() -> new DirectionChip("N", "북", 0, -1L))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INVALID_VALUE_RANGE);
		assertThatThrownBy(() -> new DirectionChip("N", "북", -1, 1L))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INVALID_VALUE_RANGE);

		assertThat(new DirectionChip("N", "북", 0, 0L).count()).isZero();
	}

	@Test
	@DisplayName("InboxListing은 원본 리스트 수정과 무관하고 반환된 리스트는 변경할 수 없다")
	void inboxListingDefensivelyCopiesAndReturnsImmutableLists() {
		List<InboxCard> mutableCards = new ArrayList<>();
		List<DirectionChip> mutableChips = new ArrayList<>();
		mutableChips.add(new DirectionChip("N", "북", 0, 1L));

		InboxListing listing = new InboxListing(mutableCards, mutableChips);
		mutableChips.add(new DirectionChip("E", "동", 2, 3L));

		assertThat(listing.chips()).hasSize(1);
		assertThatThrownBy(() -> listing.chips().add(new DirectionChip("S", "남", 4, 1L)))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> listing.cards().add(null))
			.isInstanceOf(UnsupportedOperationException.class);
	}
}
