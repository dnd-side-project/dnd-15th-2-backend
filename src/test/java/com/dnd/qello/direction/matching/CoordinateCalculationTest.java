/**
 * Created at: 2026-11-24T17:43:18Z
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-UNIT-010 through UNIT-012
 */
package com.dnd.qello.direction.matching;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.direction.config.DirectionPostProperties;
import com.dnd.qello.direction.config.DirectionReceiveProperties;
import com.dnd.qello.direction.config.DirectionRecipientSelectionProperties;
import com.dnd.qello.direction.domain.DirectionCandidate;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionPostModerationStatus;
import com.dnd.qello.direction.domain.DirectionPostStatus;
import com.dnd.qello.direction.domain.PostAudience;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.geo.CoordinateGeometry;
import com.dnd.qello.direction.geo.RankedDirectionCandidate;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.direction.repository.PostAudienceRepository;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.direction.repository.RecipientReceiveStateRepository;
import com.dnd.qello.direction.security.Coordinate;
import com.dnd.qello.feed.config.DistanceBandPolicy;
import com.dnd.qello.feed.config.FeedDistanceProperties;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.repository.OutboxEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoordinateCalculationTest {

	private static final Instant AT = Instant.parse("2026-11-24T00:00:00Z");

	@ParameterizedTest
	@CsvSource({"37.5,127,37.5,127", "90,0,90,120", "-90,0,-90,-150", "0,-180,0,180"})
	@DisplayName("UNIT-010 동일한 지점에는 방위가 없으므로 후보 측정값을 만들지 않는다")
	void excludesCoincidentPoints(double lat, double lon, double otherLat, double otherLon) {
		assertThat(CoordinateGeometry.inverse(coordinate(lat, lon), coordinate(otherLat, otherLon))).isEmpty();
	}

	@Test
	@DisplayName("UNIT-011 JFK-LHR 독립 WGS84 기준값과 순방위·역방위가 일치한다")
	void calculatesIndependentReverseAzimuth() {
		// GeographicLib's published JFK (40.6,-73.8) to LHR (51.6,-0.5) inverse
		// example.
		var result = CoordinateGeometry.inverse(coordinate(40.6, -73.8), coordinate(51.6, -0.5)).orElseThrow();

		assertThat(result.distanceMeters()).isCloseTo(5_551_759.400318678, within(0.001));
		assertThat(result.bearingDegrees()).isCloseTo(51.19888284557983, within(0.0000001));
		assertThat(result.inboundBearingDegrees()).isCloseTo(287.82177673551426, within(0.0000001));
	}

	@Test
	@DisplayName("UNIT-012 공정성 정렬은 수신수·NULL 우선 시각·원거리값·ID 순이며 선반올림하지 않는다")
	void ordersByFairnessBeforeRounding() {
		List<RankedDirectionCandidate> values = List.of(
				ranked(3, "1", 1, null), ranked(4, "1", 0, AT), ranked(5, "999", 0, AT.minusSeconds(1)),
				ranked(2, "100.000000002", 0, null), ranked(1, "100.000000002", 0, null),
				ranked(9, "100.000000001", 0, null));

		assertThat(values.stream().sorted(RankedDirectionCandidate.FAIRNESS_ORDER)
				.map(value -> value.candidate().userId())).containsExactly(9L, 1L, 2L, 5L, 4L, 3L);
	}

	@ParameterizedTest
	@CsvSource({"100.499999,100", "100.5,101", "100.500001,101"})
	@DisplayName("UNIT-012 worker는 수신자 저장 시에만 HALF_UP 정수 거리를 사용한다")
	void roundsOnlyWhenPersistingRecipient(String distance, long expected) {
		PostRecipientRepository recipients = mock(PostRecipientRepository.class);
		DirectionMatchingWorker worker = worker(candidate(21, distance), recipients);

		var result = worker.processBatch(new DirectionMatchingWorker.BatchCommand(1, "unit-worker", AT,
				AT.plusSeconds(30), new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1))));

		assertThat(result.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		ArgumentCaptor<PostRecipient> saved = ArgumentCaptor.forClass(PostRecipient.class);
		verify(recipients).save(saved.capture());
		assertThat(saved.getValue().getDistanceM()).isEqualTo(expected);
	}

	private DirectionMatchingWorker worker(DirectionCandidate candidate, PostRecipientRepository recipients) {
		OutboxEventRepository outbox = mock(OutboxEventRepository.class);
		DirectionPostRepository posts = mock(DirectionPostRepository.class);
		PostAudienceRepository audiences = mock(PostAudienceRepository.class);
		ActiveUserPresenceRepository presence = mock(ActiveUserPresenceRepository.class);
		RecipientReceiveStateRepository states = mock(RecipientReceiveStateRepository.class);
		AccountRepository accounts = mock(AccountRepository.class);
		PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
		when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		when(accounts.findById(11L)).thenReturn(Optional.of(Account.restore(11L, AccountRole.USER,
				AccountStatus.ACTIVE, "KR", "TEST", "ko-KR", "Asia/Seoul", "sender", null, null)));
		when(posts.findByIdForUpdate(1L)).thenReturn(Optional.of(DirectionPost.restore(1L, 11L, 101L,
				DirectionPostStatus.MATCHING, "unit-rounding", "body", "TEST", DirectionPostModerationStatus.PASSED,
				AT.minusSeconds(60), null, AT.plusSeconds(3600), null, null)));
		when(audiences.findByPostId(1L)).thenReturn(Optional.of(PostAudience.create(1L, 101L, "N",
				BigDecimal.ZERO, BigDecimal.valueOf(90), 0, 20_100_000,
				BigDecimal.valueOf(37.5), BigDecimal.valueOf(127), null, AT)));
		when(presence.findCandidates(anyLong(), anyDouble(), anyDouble(), anyLong(), anyLong(),
				anyDouble(), anyDouble(), any(), isNull())).thenReturn(List.of(candidate));
		when(states.lockAvailableUserIds(any(), anyInt(), anyInt())).thenReturn(List.of(candidate.userId()));
		when(states.reserve(candidate.userId(), AT, 5)).thenReturn(true);
		when(recipients.save(any())).thenReturn(PostRecipient.restore(1L, 1L, candidate.userId(),
				com.dnd.qello.direction.domain.PostRecipientStatus.AVAILABLE, "NEAR", BigDecimal.ZERO, "TEST",
				AT, null, null, null, null, null, null, null, BigDecimal.ZERO, 100L, null));
		OutboxEvent pending = OutboxEvent.matchingPending(1L, 1, "unit-event", "{}", AT);
		OutboxEvent identified = new OutboxEvent(1L, pending.aggregateType(), pending.aggregateId(),
				pending.eventType(), pending.dedupKey(), pending.payload(), pending.status(), pending.attemptCount(),
				pending.nextAttemptAt(), pending.createdAt(), pending.processedAt(), pending.matchRound(),
				pending.leaseOwner(), pending.leaseExpiresAt(), pending.leaseGeneration());
		when(outbox.claimDue(any(), anyInt(), any(), any(), any()))
				.thenReturn(List.of(identified.claimed("unit-worker", AT, AT.plusSeconds(30))));
		when(outbox.complete(1L, "unit-worker", 1L, AT)).thenReturn(true);
		return new DirectionMatchingWorker(outbox, posts, audiences, presence, recipients, states,
				new DirectionRecipientSelectionProperties(10),
				new DirectionPostProperties(DirectionPostProperties.DeliveryScope.GLOBAL, 0, 20_100_000,
						Duration.ofHours(12), 300, 1),
				new DirectionReceiveProperties(5), new DistanceBandPolicy(new FeedDistanceProperties(10_000)),
				transactions, Clock.fixed(AT, ZoneOffset.UTC), accounts);
	}

	private static RankedDirectionCandidate ranked(long id, String distance, long count, Instant last) {
		return new RankedDirectionCandidate(candidate(id, distance), count, last);
	}

	private static DirectionCandidate candidate(long id, String distance) {
		return new DirectionCandidate(id, new BigDecimal(distance), BigDecimal.ZERO, "TEST", BigDecimal.valueOf(180));
	}

	private static Coordinate coordinate(double latitude, double longitude) {
		return new Coordinate(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
	}
}
