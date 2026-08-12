/**
 * Created at: 2026-08-12T14:10:00+09:00
 * Source scenario: TEST-PLAN-GH-118-DIRECTION-POST-SUBMISSION-UNIT-001 through UNIT-005
 */
package com.dnd.qello.direction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionPostModerationStatus;
import com.dnd.qello.direction.domain.DirectionPostStatus;
import com.dnd.qello.direction.domain.DirectionRequestFingerprint;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSchemeStatus;
import com.dnd.qello.direction.domain.DirectionSchemeType;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.domain.PostAudience;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.direction.repository.PostAudienceRepository;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.question.domain.AnswerFormat;
import com.dnd.qello.question.domain.ApprovedQuestion;
import com.dnd.qello.question.domain.ApprovedQuestionSourceType;
import com.dnd.qello.question.domain.ApprovedQuestionStatus;
import com.dnd.qello.question.repository.ApprovedQuestionRepository;

@ExtendWith(MockitoExtension.class)
class DirectionPostSubmissionServiceTest {

	private static final Instant AT = Instant.parse("2026-08-12T05:00:00Z");
	private static final long SENDER_ID = 11L;
	private static final long QUESTION_ID = 22L;
	private static final long SCHEME_ID = 33L;

	@Mock
	private DirectionSchemeRepository schemeRepository;
	@Mock
	private ActiveUserPresenceRepository presenceRepository;
	@Mock
	private DirectionPostRepository postRepository;
	@Mock
	private PostAudienceRepository audienceRepository;
	@Mock
	private ApprovedQuestionRepository approvedQuestionRepository;
	@Mock
	private OutboxEventRepository outboxEventRepository;
	@Mock
	private PlatformTransactionManager transactionManager;

	@InjectMocks
	private DirectionPostService service;

	private DirectionScheme scheme;
	private List<DirectionSegment> segments;
	private ActiveUserPresence sender;
	private ApprovedQuestion question;
	private DirectionPost savedPost;
	private PostAudience audience;
	private OutboxEvent matchingEvent;

	@BeforeEach
	void setUp() {
		TransactionStatus transactionStatus = mock(TransactionStatus.class);
		org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
		org.mockito.Mockito.lenient().doNothing().when(transactionManager).commit(transactionStatus);
		org.mockito.Mockito.lenient().doNothing().when(transactionManager).rollback(transactionStatus);

		scheme = DirectionScheme.restore(SCHEME_ID, "OCTANT", 1, DirectionSchemeType.EQUAL_SEGMENTS,
			8, BigDecimal.ZERO, DirectionSchemeStatus.ACTIVE);
		segments = IntStream.range(0, 8)
			.mapToObj(index -> DirectionSegment.create(SCHEME_ID, "S" + index, "segment-" + index,
				BigDecimal.valueOf(index * 45L + 22.5), BigDecimal.valueOf(45), index))
			.toList();
		sender = ActiveUserPresence.create(SENDER_ID, BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0),
			null, "TEST-REGION", BigDecimal.ONE, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		question = ApprovedQuestion.restore(QUESTION_ID, null, ApprovedQuestionSourceType.OPERATOR,
			ApprovedQuestionStatus.ACTIVE, "질문", AnswerFormat.TEXT, AT.minusSeconds(60),
			AT.plusSeconds(3600), AT.minusSeconds(60), SENDER_ID, AT.minusSeconds(60));
		DirectionRequestFingerprint fingerprint = DirectionRequestFingerprint.create(QUESTION_ID, SCHEME_ID,
			"S0", 0, 500, "TEST-REGION", "본문");
		savedPost = DirectionPost.restore(101L, SENDER_ID, QUESTION_ID, fingerprint,
			DirectionPostStatus.MATCHING, "submission-key", "본문", "TEST-REGION",
			DirectionPostModerationStatus.PENDING, AT, null, AT.plusSeconds(3600), null, null);
		audience = PostAudience.create(101L, SCHEME_ID, "S0", BigDecimal.valueOf(22.5),
			BigDecimal.valueOf(45), 0, 500, BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0),
			null, AT);
		matchingEvent = OutboxEvent.matchingPending(101L, 1,
			"direction-match:101:1:RECIPIENT_MATCH_REQUESTED",
			"{\"postId\":101,\"matchRound\":1,\"eventType\":\"RECIPIENT_MATCH_REQUESTED\"}", AT);

		org.mockito.Mockito.lenient().when(schemeRepository.findById(SCHEME_ID)).thenReturn(Optional.of(scheme));
		org.mockito.Mockito.lenient().when(schemeRepository.findSegments(SCHEME_ID)).thenReturn(segments);
		org.mockito.Mockito.lenient().when(presenceRepository.findByUserId(SENDER_ID)).thenReturn(Optional.of(sender));
		org.mockito.Mockito.lenient().when(approvedQuestionRepository.findAssignableAt(AT)).thenReturn(List.of(question));
		org.mockito.Mockito.lenient().when(postRepository.save(any(DirectionPost.class))).thenReturn(savedPost);
		org.mockito.Mockito.lenient().when(audienceRepository.save(any(PostAudience.class))).thenReturn(audience);
		org.mockito.Mockito.lenient().when(outboxEventRepository.findByDedupKey(anyString())).thenReturn(Optional.empty());
		org.mockito.Mockito.lenient().when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(matchingEvent);
	}

	@Test
	@DisplayName("제출은 post·audience·matching Outbox만 기록하고 수신자와 슬롯을 만들지 않는다")
	void submissionDoesNotConfirmRecipients() {
		DirectionPostService.SendResult result = service.send(command("submission-key", "본문"));

		assertThat(result.post().getId()).isEqualTo(101L);
		assertThat(result.audience()).isEqualTo(audience);
		assertThat(result.recipients()).isEmpty();
		verify(postRepository, times(1)).save(any(DirectionPost.class));
		verify(audienceRepository, times(1)).save(any(PostAudience.class));
		verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
		verify(presenceRepository, times(1)).findByUserId(SENDER_ID);
		verify(presenceRepository, never()).findCandidates(anyLong(), anyDouble(), anyDouble(),
			anyLong(), anyLong(), anyDouble(), anyDouble(), any(Instant.class), anyString());
	}

	@Test
	@DisplayName("같은 멱등키와 fingerprint의 재시도는 기존 제출 결과와 빈 수신자 목록을 반환한다")
	void sameFingerprintRetryReturnsExistingSubmission() {
		when(postRepository.findBySenderAndIdempotencyKey(SENDER_ID, "submission-key"))
			.thenReturn(Optional.empty(), Optional.of(savedPost));

		DirectionPostService.SendResult first = service.send(command("submission-key", "본문"));
		DirectionPostService.SendResult retry = service.send(command("submission-key", "본문"));

		assertThat(retry.post().getId()).isEqualTo(first.post().getId());
		assertThat(retry.recipients()).isEmpty();
		verify(postRepository, times(1)).save(any(DirectionPost.class));
		verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
	}

	@Test
	@DisplayName("같은 멱등키에 다른 fingerprint를 사용하면 IDEMPOTENCY_KEY_REUSED를 반환한다")
	void differentFingerprintReuseIsRejected() {
		when(postRepository.findBySenderAndIdempotencyKey(SENDER_ID, "submission-key"))
			.thenReturn(Optional.of(savedPost));

		assertThatThrownBy(() -> service.send(command("submission-key", "다른 본문")))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.IDEMPOTENCY_KEY_REUSED);
		verify(postRepository, never()).save(any(DirectionPost.class));
		verify(audienceRepository, never()).save(any(PostAudience.class));
		verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
	}

	@Test
	@DisplayName("matching Outbox는 최초 round 1과 좌표 없는 payload 계약을 사용한다")
	void matchingEventUsesApprovedRoundAndSafePayload() {
		service.send(command("submission-key", "본문"));

		var eventCaptor = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository).save(eventCaptor.capture());
		OutboxEvent event = eventCaptor.getValue();
		assertThat(event.matchRound()).isEqualTo(1);
		assertThat(event.dedupKey()).isEqualTo("direction-match:101:1:RECIPIENT_MATCH_REQUESTED");
		assertThat(event.payload()).doesNotContain("latitude", "longitude", "origin_position", "37.5", "127.0");
	}

	@Test
	@DisplayName("제출 시각에 활성이지 않은 질문은 downstream write 전에 거부한다")
	void inactiveQuestionIsRejectedBeforeWrites() {
		when(approvedQuestionRepository.findAssignableAt(AT)).thenReturn(List.of());

		assertThatThrownBy(() -> service.send(command("invalid-question-key", "본문")))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.QUESTION_NOT_ACTIVE);
		verify(postRepository, never()).save(any(DirectionPost.class));
		verify(audienceRepository, never()).save(any(PostAudience.class));
		verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
	}

	private DirectionPostService.SendCommand command(String idempotencyKey, String body) {
		return new DirectionPostService.SendCommand(SENDER_ID, QUESTION_ID, SCHEME_ID, "S0", 0, 500,
			"TEST-REGION", idempotencyKey, body, AT, AT.plusSeconds(3600));
	}
}
