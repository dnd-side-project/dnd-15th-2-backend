/**
 * Created at: 2026-08-12T01:17:58+09:00
 * Source scenario: TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-UNIT-001,
 * TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-UNIT-004 through UNIT-006
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-011
 */
package com.dnd.qello.direction;

import com.dnd.qello.direction.config.DirectionReceiveProperties;
import com.dnd.qello.direction.config.DirectionRecipientSelectionProperties;
import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.*;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.direction.service.DirectionPreviewResult;
import com.dnd.qello.feed.config.DistanceBandPolicy;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.question.repository.ApprovedQuestionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectionPreviewServiceTest {

    private static final Instant AT = Instant.parse("2026-08-12T00:00:00Z");

    @Mock
    private DirectionSchemeRepository schemeRepository;
    @Mock
    private ActiveUserPresenceRepository presenceRepository;
    @Mock
    private RecipientReceiveStateRepository receiveStateRepository;
    @Mock
    private DirectionRecipientSelectionProperties recipientSelectionProperties;
    @Mock
    private DirectionPostRepository postRepository;
    @Mock
    private PostAudienceRepository audienceRepository;
    @Mock
    private PostRecipientRepository recipientRepository;
    @Mock
    private ApprovedQuestionRepository approvedQuestionRepository;
    @Mock
    private DirectionReceiveProperties receiveProperties;
    @Mock
    private DistanceBandPolicy distanceBandPolicy;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private DirectionPostService service;

    @Test
    @DisplayName("전체 방향 preview는 누락된 segment를 0으로 채우고 정책 순서를 유지한다")
    void fillsMissingSegmentsWithZeroInPolicyOrder() {
        DirectionScheme scheme = DirectionScheme.restore(10L, "TEST", 1,
                com.dnd.qello.direction.domain.DirectionSchemeType.EQUAL_SEGMENTS, 8,
                BigDecimal.ZERO, com.dnd.qello.direction.domain.DirectionSchemeStatus.ACTIVE);
        List<DirectionSegment> segments = eightSegments(scheme.getId());
        ActiveUserPresence sender = sender();
        when(schemeRepository.findById(scheme.getId())).thenReturn(Optional.of(scheme));
        when(schemeRepository.findSegments(scheme.getId())).thenReturn(segments);
        when(presenceRepository.findByUserId(1L)).thenReturn(Optional.of(sender));
        when(presenceRepository.findCandidateCountsBySegment(eq(scheme.getId()), eq(1L), anyDouble(), anyDouble(),
                eq(0L), eq(10_000L), eq(AT), isNull()))
                .thenReturn(List.of(
                        new ActiveUserPresenceRepository.DirectionSegmentCandidateCount("S0", 2),
                        new ActiveUserPresenceRepository.DirectionSegmentCandidateCount("S7", 1)));

        DirectionPreviewResult result = service.previewAll(
                new DirectionPostService.PreviewAllCommand(1L, scheme.getId(), 0, 10_000, "TEST-REGION", AT));

        assertThat(result.segments()).hasSize(8)
                .extracting(DirectionPreviewResult.SegmentCount::segmentKey)
                .containsExactly("S0", "S1", "S2", "S3", "S4", "S5", "S6", "S7");
        assertThat(result.segments()).extracting(DirectionPreviewResult.SegmentCount::count)
                .containsExactly(2L, 0L, 0L, 0L, 0L, 0L, 0L, 1L);
        verify(presenceRepository, times(1)).findCandidateCountsBySegment(eq(scheme.getId()), eq(1L),
                anyDouble(), anyDouble(), eq(0L), eq(10_000L), eq(AT), isNull());
    }

    @Test
    @DisplayName("preview 결과 모델은 사용자 식별자와 정확 좌표 필드를 노출하지 않는다")
    void previewResultDoesNotExposeIdentityOrExactCoordinates() {
        assertThat(java.util.Arrays.stream(DirectionPreviewResult.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .noneMatch(name -> name.equalsIgnoreCase("userId") || name.equalsIgnoreCase("latitude")
                        || name.equalsIgnoreCase("longitude") || name.equalsIgnoreCase("position")
                        || name.equalsIgnoreCase("point"));
        assertThat(java.util.Arrays.stream(DirectionPreviewResult.SegmentCount.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .noneMatch(name -> name.equalsIgnoreCase("userId") || name.equalsIgnoreCase("latitude")
                        || name.equalsIgnoreCase("longitude") || name.equalsIgnoreCase("position")
                        || name.equalsIgnoreCase("point"));
    }

    @Test
    @DisplayName("preview 결과 모델의 값 검증 실패는 direction 오류 코드로 반환한다")
    void previewResultValidationUsesDirectionErrorCode() {
        assertThatThrownBy(() -> new DirectionPreviewResult(0, "TEST", 1, List.of()))
                .isInstanceOf(DirectionException.class)
                .hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_ID);
        assertThatThrownBy(() -> new DirectionPreviewResult(1, " ", 1, List.of()))
                .isInstanceOf(DirectionException.class)
                .hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_TEXT);
        assertThatThrownBy(() -> new DirectionPreviewResult(1, "TEST", 0, List.of()))
                .isInstanceOf(DirectionException.class)
                .hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_VALUE_RANGE);
        assertThatThrownBy(() -> new DirectionPreviewResult.SegmentCount("S0", "segment", 0, -1))
                .isInstanceOf(DirectionException.class)
                .hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_VALUE_RANGE);
    }

    @Test
    @DisplayName("방향 후보 집계 결과의 검증 실패는 direction 오류 코드로 반환한다")
    void candidateCountValidationUsesDirectionErrorCode() {
        assertThatThrownBy(() -> new ActiveUserPresenceRepository.DirectionSegmentCandidateCount(" ", 0))
                .isInstanceOf(DirectionException.class)
                .hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_TEXT);
        assertThatThrownBy(() -> new ActiveUserPresenceRepository.DirectionSegmentCandidateCount("S0", -1))
                .isInstanceOf(DirectionException.class)
                .hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_VALUE_RANGE);
    }

    @Test
    @DisplayName("비활성 scheme은 전체 방향 preview 대상이 아니다")
    void rejectsInactiveScheme() {
        DirectionScheme inactive = DirectionScheme.restore(10L, "TEST", 1,
                com.dnd.qello.direction.domain.DirectionSchemeType.EQUAL_SEGMENTS, 8,
                BigDecimal.ZERO, com.dnd.qello.direction.domain.DirectionSchemeStatus.INACTIVE);
        when(presenceRepository.findByUserId(1L)).thenReturn(Optional.of(sender()));
        when(schemeRepository.findById(10L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.previewAll(
                new DirectionPostService.PreviewAllCommand(1L, 10L, 0, 10_000, "TEST-REGION", AT)))
                .isInstanceOf(DirectionException.class)
                .hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.SCHEME_NOT_FOUND);
    }

    @Test
    @DisplayName("전체 방향 preview는 최소 거리 이상 최대 거리 이하의 유효한 범위만 허용한다")
    void rejectsInvalidDistanceRange() {
        assertThatThrownBy(() -> new DirectionPostService.PreviewAllCommand(1L, 10L, 10_001, 10_000,
                "TEST-REGION", AT))
                .isInstanceOf(DirectionException.class)
                .hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_DISTANCE_RANGE);
    }

    private ActiveUserPresence sender() {
        return ActiveUserPresence.create(1L, BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0), null,
                "TEST-REGION", BigDecimal.ONE, true, AT.minusSeconds(60), AT.plusSeconds(3600));
    }

    private List<DirectionSegment> eightSegments(long schemeId) {
        return java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> DirectionSegment.create(schemeId, "S" + index, "segment-" + index,
                        BigDecimal.valueOf(index * 45L + 22.5), BigDecimal.valueOf(45), index))
                .toList();
    }
}
