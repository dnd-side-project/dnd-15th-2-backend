/**
 * Created at: 2026-08-14T13:10:00+09:00
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-002,
 * TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-007 through UNIT-008,
 * TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-013
 */
package com.dnd.qello.direction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.direction.config.DirectionPostProperties;
import com.dnd.qello.direction.config.DirectionSchemeProperties;
import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSchemeStatus;
import com.dnd.qello.direction.domain.DirectionSchemeType;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionPostModerationStatus;
import com.dnd.qello.direction.domain.DirectionPostStatus;
import com.dnd.qello.direction.domain.DirectionRequestFingerprint;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.account.repository.AccountRepository;

@ExtendWith(MockitoExtension.class)
class DirectionPostApplicationServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-14T04:00:00Z");
	private static final long USER_ID = 7L;
	private static final long SCHEME_ID = 8L;

	@Mock private AccountRepository accountRepository;
	@Mock private ActiveUserPresenceRepository presenceRepository;
	@Mock private DirectionSchemeRepository schemeRepository;
	@Mock private DirectionPostService postService;

	private DirectionPostApplicationService service;
	private DirectionScheme scheme;
	private ActiveUserPresence presence;

	@BeforeEach
	void setUp() {
		DirectionPostProperties properties = new DirectionPostProperties(
			DirectionPostProperties.DeliveryScope.GLOBAL, 0, 20_100_000L, Duration.ofHours(12), 300, 1);
		service = new DirectionPostApplicationService(accountRepository, presenceRepository, schemeRepository,
			new DirectionSchemeProperties("OCTANT"), new DirectionPostPolicy(properties), postService,
			Clock.fixed(NOW, ZoneOffset.UTC));
		scheme = DirectionScheme.restore(SCHEME_ID, "OCTANT", 1, DirectionSchemeType.EQUAL_SEGMENTS,
			8, BigDecimal.ZERO, DirectionSchemeStatus.ACTIVE);
		presence = ActiveUserPresence.create(USER_ID, BigDecimal.valueOf(37.5), BigDecimal.valueOf(127),
			null, "KR-11", BigDecimal.ONE, false, NOW.minusSeconds(10), NOW.plusSeconds(3600));
		org.mockito.Mockito.lenient().when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(Account.createUser("KR", "KR-11",
			"ko-KR", "Asia/Seoul", "test")));
		org.mockito.Mockito.lenient().when(presenceRepository.findByUserId(USER_ID)).thenReturn(Optional.of(presence));
		org.mockito.Mockito.lenient().when(schemeRepository.findActiveByCode("OCTANT")).thenReturn(Optional.of(scheme));
	}

	@Test
	@DisplayName("preview는 receiveAllowed와 무관하게 ACTIVE USER의 현재 위치와 설정 scheme을 사용한다")
	void previewUsesServerAuthoritativeActorAndScheme() {
		service.preview(USER_ID);

		var captor = org.mockito.ArgumentCaptor.forClass(DirectionPostService.PreviewAllCommand.class);
		verify(postService).previewAll(captor.capture());
		org.assertj.core.api.Assertions.assertThat(captor.getValue().senderId()).isEqualTo(USER_ID);
		org.assertj.core.api.Assertions.assertThat(captor.getValue().schemeId()).isEqualTo(SCHEME_ID);
		org.assertj.core.api.Assertions.assertThat(captor.getValue().coarseRegionCode()).isNull();
		org.assertj.core.api.Assertions.assertThat(captor.getValue().at()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("submit은 server Clock과 정책 거리·TTL·presence 지역 snapshot을 하위 서비스에 전달한다")
	void submitUsesServerPolicyAndClock() {
		service.submit(USER_ID, "request-key", new DirectionPostApplicationService.SubmitCommand(
			101L, SCHEME_ID, "N", " 본문 ", List.of(55L)));

		var captor = org.mockito.ArgumentCaptor.forClass(DirectionPostService.SendCommand.class);
		verify(postService).send(captor.capture());
		org.assertj.core.api.Assertions.assertThat(captor.getValue().senderId()).isEqualTo(USER_ID);
		org.assertj.core.api.Assertions.assertThat(captor.getValue().minDistanceMeters()).isZero();
		org.assertj.core.api.Assertions.assertThat(captor.getValue().maxDistanceMeters()).isEqualTo(20_100_000L);
		org.assertj.core.api.Assertions.assertThat(captor.getValue().coarseRegionCode()).isEqualTo("KR-11");
		org.assertj.core.api.Assertions.assertThat(captor.getValue().bodyText()).isEqualTo("본문");
		org.assertj.core.api.Assertions.assertThat(captor.getValue().mediaIds()).containsExactly(55L);
		org.assertj.core.api.Assertions.assertThat(captor.getValue().submittedAt()).isEqualTo(NOW);
		org.assertj.core.api.Assertions.assertThat(captor.getValue().expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
	}

	@Test
	@DisplayName("잘못된 멱등키는 하위 저장 서비스 호출 전에 거절한다")
	void rejectsInvalidIdempotencyKey() {
		assertThatThrownBy(() -> service.submit(USER_ID, " ", new DirectionPostApplicationService.SubmitCommand(
			101L, SCHEME_ID, "N", "본문", List.of())))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_TEXT);
		verify(postService, never()).send(any());
	}

	@Test
	@DisplayName("receiveAllowed=false인 발신자는 질문글을 제출할 수 있다")
	void allowsSenderWithReceiveDisabled() {
		service.submit(USER_ID, "request-key", new DirectionPostApplicationService.SubmitCommand(
			101L, SCHEME_ID, "N", "본문", List.of()));
		verify(postService).send(any());
	}

	@Test
	@DisplayName("요청 scheme이 현재 설정된 ACTIVE scheme과 다르면 조용히 치환하지 않는다")
	void rejectsStaleScheme() {
		assertThatThrownBy(() -> service.submit(USER_ID, "request-key", new DirectionPostApplicationService.SubmitCommand(
			101L, 999L, "N", "본문", List.of())))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.SCHEME_NOT_FOUND);
		verify(postService, never()).send(any());
	}

	@Test
	@DisplayName("기존 멱등키 재시도는 활성 scheme이나 현재 정책이 바뀌어도 저장된 결과를 반환한다")
	void replaysExistingRequestBeforeCurrentPolicyValidation() {
		DirectionRequestFingerprint fingerprint = DirectionRequestFingerprint.create(
			101L, 999L, "N", 0L, 20_100_000L, "본문", List.of());
		DirectionPost post = DirectionPost.restore(101L, USER_ID, 101L, fingerprint,
			DirectionPostStatus.MATCHING, "request-key", "본문", "KR-11",
			DirectionPostModerationStatus.PENDING, NOW, null, NOW.plus(Duration.ofHours(12)), null, null);
		DirectionPostService.SendResult result = new DirectionPostService.SendResult(post, null, List.of());
		DirectionPostApplicationService.SubmitCommand command = new DirectionPostApplicationService.SubmitCommand(
			101L, 999L, "N", "본문", List.of());
		when(postService.replayIfExists(USER_ID, "request-key", 101L, 999L, "N", "본문", List.of()))
			.thenReturn(Optional.of(result));

		assertThat(service.submit(USER_ID, "request-key", command)).isSameAs(result);
		verify(accountRepository).findById(USER_ID);
		verify(schemeRepository, never()).findActiveByCode(any());
		verify(postService, never()).send(any());
	}

	@Test
	@DisplayName("비활성 계정은 기존 멱등키 재시도도 결과를 조회할 수 없다")
	void rejectsReplayForInactiveAccount() {
		when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(
			Account.createUser("KR", "KR-11", "ko-KR", "Asia/Seoul", "test").block()));

		assertThatThrownBy(() -> service.submit(USER_ID, "request-key", new DirectionPostApplicationService.SubmitCommand(
			101L, SCHEME_ID, "N", "본문", List.of())))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.PRESENCE_ACCOUNT_NOT_ELIGIBLE);
		verify(postService, never()).replayIfExists(anyLong(), anyString(), anyLong(), anyLong(), anyString(), any(), any());
	}
}
