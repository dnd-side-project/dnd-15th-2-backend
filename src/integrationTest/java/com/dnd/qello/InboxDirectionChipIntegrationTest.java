/**
 * Created at: 2026-08-08T21:30:12+09:00
 * Source scenario: TEST-PLAN-GH-80-INBOX-DIRECTION-CHIPS-INT-001 through INT-010
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.feed.service.InboxQueryService;
import com.dnd.qello.feed.view.DirectionChip;
import com.dnd.qello.feed.view.InboxCard;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.view.InboxListing;

@SpringBootTest
@ActiveProfiles("test")
class InboxDirectionChipIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-DIRCHIP";
	private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private InboxQueryService inboxQueryService;
	@Autowired
	private DirectionSchemeRepository directionSchemeRepository;

	private long senderId;
	private long recipientId;
	private long questionId;
	private int postSeq;

	@BeforeEach
	void resetFixtures() {
		jdbc.update("DELETE FROM answer_reaction");
		jdbc.update("DELETE FROM post_reaction");
		jdbc.update("DELETE FROM answer");
		jdbc.update("DELETE FROM media_attachment");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM user_block");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Inbox Direction Chip', 'REGION')", REGION);

		senderId = account("dc-sender");
		recipientId = account("dc-recipient");
		questionId = jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', '오늘 뭐 하고 있나요?', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW), senderId);
		postSeq = 0;
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}

	private long post(long author, Instant expiresAt) {
		String key = "p-" + author + "-" + (++postSeq);
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', ?, '본문', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, author, questionId, key, REGION, Timestamp.from(NOW), Timestamp.from(NOW),
			Timestamp.from(expiresAt));
	}

	private long post(Instant expiresAt) {
		return post(senderId, expiresAt);
	}

	/** 미답변(AVAILABLE) 수신 항목을 지정한 방위각으로 만든다. */
	private long unansweredRecipient(long postId, double bearingDeg) {
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code, matched_at,
				 inbound_bearing_deg, distance_m)
			VALUES (?, ?, 'AVAILABLE', 'NEAR', 45, ?, ?, ?, 5000)
			RETURNING id
			""", Long.class, postId, recipientId, REGION, Timestamp.from(NOW), bearingDeg);
	}

	/** 답변 완료(ANSWERED) 수신 항목을 지정한 방위각으로 만든다. 슬롯은 이미 해제된 상태다. */
	private long answeredRecipient(long postId, double bearingDeg) {
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code, matched_at,
				 discovered_at, opened_at, capacity_released_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, 'ANSWERED', 'NEAR', 45, ?, ?, ?, ?, ?, ?, 5000)
			RETURNING id
			""", Long.class, postId, recipientId, REGION, Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW),
			Timestamp.from(NOW), bearingDeg);
	}

	private DirectionChip chip(List<DirectionChip> chips, String segmentKey) {
		return chips.stream().filter(candidate -> candidate.segmentKey().equals(segmentKey)).findFirst()
			.orElseThrow(() -> new AssertionError("칩 없음: " + segmentKey));
	}

	@Test
	@DisplayName("북 구간은 0도를 가로질러 350도와 10도를 모두 포함해 집계한다")
	void northSegmentWrapsAroundZeroDegrees() {
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 350.0);
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 10.0);
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 0.0);

		List<DirectionChip> chips =
			inboxQueryService.list(recipientId, InboxCategory.UNANSWERED, null, NOW.plusSeconds(1)).chips();

		assertThat(chips).hasSize(1);
		assertThat(chip(chips, "N").count()).isEqualTo(3);
	}

	@Test
	@DisplayName("경계각은 시작 구간에만, 그 직전 각은 이전 구간에만 속해 두 구간에 중복 계상되지 않는다")
	void boundaryAnglesBelongToExactlyOneSegment() {
		// OCTANT: 8구간 45도, 시작각 = center - 22.5. 이 목록 순서 자체가 원형 인접
		// 순서다 — 인덱스 i의 이전 구간은 (i - 1 + size) % size다(N의 이전은 NW).
		record Boundary(double startDeg, String startSegment) {
		}
		List<Boundary> boundaries = List.of(
			new Boundary(22.5, "NE"), new Boundary(67.5, "E"), new Boundary(112.5, "SE"),
			new Boundary(157.5, "S"), new Boundary(202.5, "SW"), new Boundary(247.5, "W"),
			new Boundary(292.5, "NW"), new Boundary(337.5, "N"));
		List<String> allSegmentKeys = boundaries.stream().map(Boundary::startSegment).toList();

		List<Long> startPostIds = new ArrayList<>();
		List<Long> beforePostIds = new ArrayList<>();
		for (Boundary boundary : boundaries) {
			long startPostId = post(NOW.plus(1, ChronoUnit.HOURS));
			unansweredRecipient(startPostId, boundary.startDeg());
			startPostIds.add(startPostId);

			long beforePostId = post(NOW.plus(1, ChronoUnit.HOURS));
			unansweredRecipient(beforePostId, boundary.startDeg() - 0.001);
			beforePostIds.add(beforePostId);
		}

		for (int i = 0; i < boundaries.size(); i++) {
			Boundary boundary = boundaries.get(i);
			String previousSegment = allSegmentKeys.get(Math.floorMod(i - 1, allSegmentKeys.size()));

			assertBelongsToExactlyOneSegment(startPostIds.get(i), boundary.startSegment(), allSegmentKeys);
			assertBelongsToExactlyOneSegment(beforePostIds.get(i), previousSegment, allSegmentKeys);
		}
	}

	@Test
	@DisplayName("SQL이 파생한 구간과 DirectionSegment.contains의 판정이 대표 방위각 전체에서 일치한다")
	void sqlDerivedSegmentMatchesDomainContainsAcrossSweep() {
		long schemeId = jdbc.queryForObject(
			"SELECT id FROM direction_scheme WHERE code = 'OCTANT' AND status = 'ACTIVE'", Long.class);
		List<DirectionSegment> segments = directionSchemeRepository.findSegments(schemeId);
		List<String> allSegmentKeys = segments.stream().map(DirectionSegment::getSegmentKey).toList();

		double[] sweep = {
			0, 45, 90, 135, 180, 225, 270, 315,
			22.5, 67.5, 112.5, 157.5, 202.5, 247.5, 292.5, 337.5,
			22.499, 67.499, 112.499, 157.499, 202.499, 247.499, 292.499, 337.499,
			350.0, 10.0, 5.0, 359.999
		};

		for (double bearing : sweep) {
			String expectedKey = segments.stream()
				.filter(segment -> segment.contains(bearing))
				.findFirst()
				.orElseThrow(() -> new AssertionError("도메인 구간이 방위각을 커버하지 못함: " + bearing))
				.getSegmentKey();

			long postId = post(NOW.plus(1, ChronoUnit.HOURS));
			unansweredRecipient(postId, bearing);

			assertBelongsToExactlyOneSegment(postId, expectedKey, allSegmentKeys);
		}
	}

	/** postId가 expectedSegment로 필터했을 때만 나오고 나머지 구간 전부에서는 나오지 않는지 확인한다. */
	private void assertBelongsToExactlyOneSegment(long postId, String expectedSegment, List<String> allSegmentKeys) {
		for (String key : allSegmentKeys) {
			boolean matches = inboxQueryService.list(recipientId, InboxCategory.UNANSWERED, key, NOW.plusSeconds(1))
				.cards().stream().anyMatch(card -> card.postId() == postId);
			if (key.equals(expectedSegment)) {
				assertThat(matches).as("post %d는 %s 구간에 속해야 한다", postId, expectedSegment).isTrue();
			} else {
				assertThat(matches)
					.as("post %d는 %s 구간에 속하지 않아야 한다(기대 구간: %s)", postId, key, expectedSegment)
					.isFalse();
			}
		}
	}

	@Test
	@DisplayName("0건인 방향은 칩 목록에 나오지 않는다")
	void excludesZeroCountDirectionsFromChips() {
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 0.0);
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 90.0);

		List<DirectionChip> chips =
			inboxQueryService.list(recipientId, InboxCategory.UNANSWERED, null, NOW.plusSeconds(1)).chips();

		assertThat(chips).extracting(DirectionChip::segmentKey).containsExactlyInAnyOrder("N", "E");
	}

	@Test
	@DisplayName("카테고리를 바꾸면 칩 집계 범위도 그 카테고리로 한정된다")
	void chipAggregationIsScopedToCategory() {
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 0.0);
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 0.0);
		answeredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 180.0);

		InboxListing unanswered = inboxQueryService.list(recipientId, InboxCategory.UNANSWERED, null, NOW.plusSeconds(1));
		InboxListing answered = inboxQueryService.list(recipientId, InboxCategory.ANSWERED, null, NOW.plusSeconds(1));

		assertThat(unanswered.chips()).extracting(DirectionChip::segmentKey).containsExactly("N");
		assertThat(chip(unanswered.chips(), "N").count()).isEqualTo(2);
		assertThat(answered.chips()).extracting(DirectionChip::segmentKey).containsExactly("S");
	}

	@Test
	@DisplayName("모든 칩에 대해 count는 그 segmentKey로 필터한 목록 건수와 같다")
	void chipCountMatchesFilteredListSizeForEveryChip() {
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 0.0);
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 5.0);
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 90.0);
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 91.0);
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 91.0);
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 180.0);

		InboxListing unfiltered = inboxQueryService.list(recipientId, InboxCategory.UNANSWERED, null, NOW.plusSeconds(1));
		long chipCountSum = unfiltered.chips().stream().mapToLong(DirectionChip::count).sum();
		assertThat(chipCountSum).isEqualTo(unfiltered.cards().size());

		for (DirectionChip candidate : unfiltered.chips()) {
			List<InboxCard> filtered = inboxQueryService
				.list(recipientId, InboxCategory.UNANSWERED, candidate.segmentKey(), NOW.plusSeconds(1)).cards();
			assertThat(filtered).as("칩 %s", candidate.segmentKey()).hasSize((int) candidate.count());
		}
	}

	@Test
	@DisplayName("만료·차단·SKIPPED 항목은 목록과 칩에서 동일하게 빠진다")
	void expiredBlockedAndSkippedAreExcludedFromBothListAndChips() {
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 0.0);

		long skippedPost = post(NOW.plus(1, ChronoUnit.HOURS));
		jdbc.update("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code, matched_at,
				 discovered_at, skip_requested_at, skipped_at, capacity_released_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, 'SKIPPED', 'NEAR', 45, ?, ?, ?, ?, ?, ?, ?, 5000)
			""", skippedPost, recipientId, REGION, Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW),
			Timestamp.from(NOW), Timestamp.from(NOW), 0.0);

		long expiringPost = post(NOW.plusSeconds(30));
		unansweredRecipient(expiringPost, 0.0);

		long blockedSenderId = account("dc-blocked-sender");
		long blockedPost = post(blockedSenderId, NOW.plus(1, ChronoUnit.HOURS));
		unansweredRecipient(blockedPost, 0.0);
		jdbc.update("INSERT INTO user_block (blocker_id, blocked_id) VALUES (?, ?)", recipientId, blockedSenderId);

		InboxListing listing = inboxQueryService.list(recipientId, InboxCategory.UNANSWERED, null, NOW.plusSeconds(31));

		assertThat(listing.cards()).hasSize(1);
		assertThat(chip(listing.chips(), "N").count()).isEqualTo(1);
	}

	@Test
	@DisplayName("알 수 없는 segmentKey는 예외 없이 빈 목록을 반환하고 칩은 그대로 유지된다")
	void unknownSegmentKeyYieldsEmptyListButChipsRemain() {
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 0.0);
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 90.0);

		InboxListing listing =
			inboxQueryService.list(recipientId, InboxCategory.UNANSWERED, "UNKNOWN-KEY", NOW.plusSeconds(1));

		assertThat(listing.cards()).isEmpty();
		assertThat(listing.chips()).extracting(DirectionChip::segmentKey).containsExactlyInAnyOrder("N", "E");
	}

	@Test
	@DisplayName("공백 segmentKey는 필터 없음과 같게 취급되어 전체 목록을 반환한다")
	void blankSegmentKeyIsTreatedAsNoFilter() {
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 0.0);
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 90.0);

		InboxListing listing = inboxQueryService.list(recipientId, InboxCategory.UNANSWERED, "  ", NOW.plusSeconds(1));

		assertThat(listing.cards()).hasSize(2);
	}

	@Test
	@DisplayName("필터 없는 목록은 ACTIVE 스킴이 없어도 온전하고, 칩만 비어 있다")
	void unfilteredListIsUnaffectedByMissingActiveScheme() {
		long postId = post(NOW.plus(1, ChronoUnit.HOURS));
		unansweredRecipient(postId, 0.0);

		jdbc.update("UPDATE direction_scheme SET status = 'INACTIVE' WHERE code = 'OCTANT' AND status = 'ACTIVE'");
		try {
			InboxListing listing = inboxQueryService.list(recipientId, InboxCategory.UNANSWERED, null, NOW.plusSeconds(1));

			assertThat(listing.cards()).extracting(InboxCard::postId).containsExactly(postId);
			assertThat(listing.chips()).isEmpty();
		} finally {
			jdbc.update("UPDATE direction_scheme SET status = 'ACTIVE' WHERE code = 'OCTANT' AND status = 'INACTIVE'");
		}
	}

	@Test
	@DisplayName("다른 code의 스킴이 동시에 ACTIVE여도 설정된 schemeCode 스킴만 칩 집계에 참여한다")
	void otherActiveSchemeDoesNotCauseDuplicateAggregation() {
		unansweredRecipient(post(NOW.plus(1, ChronoUnit.HOURS)), 0.0);

		long otherSchemeId = jdbc.queryForObject("""
			INSERT INTO direction_scheme (code, version, type, segment_count, start_offset_deg, status)
			VALUES ('TEST-OCTANT-DUPLICATE', 1, 'EQUAL_SEGMENTS', 8, 337.500, 'ACTIVE')
			RETURNING id
			""", Long.class);
		jdbc.update("""
			INSERT INTO direction_segment (scheme_id, segment_key, display_name, center_bearing_deg, angular_width_deg, sort_order)
			VALUES (?, 'N', '북(중복)', 0.000, 45.000, 0)
			""", otherSchemeId);

		try {
			List<DirectionChip> chips =
				inboxQueryService.list(recipientId, InboxCategory.UNANSWERED, null, NOW.plusSeconds(1)).chips();

			assertThat(chip(chips, "N").count()).isEqualTo(1);
		} finally {
			jdbc.update("DELETE FROM direction_segment WHERE scheme_id = ?", otherSchemeId);
			jdbc.update("DELETE FROM direction_scheme WHERE id = ?", otherSchemeId);
		}
	}
}
