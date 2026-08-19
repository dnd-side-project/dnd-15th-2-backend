package com.dnd.qello.feed.web.response;

import com.dnd.qello.feed.view.AnswerCard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 답변 목록 공개 모델. 정확 좌표와 작성자 내부 식별자는 싣지 않는다(기존 수신함
 * 규칙과 동일). nextCursor는 반환 건수가 요청 limit과 같을 때만 채운다.
 */
public record AnswerListingResponse(
        List<Answer> answers,
        Cursor nextCursor
) {
    public AnswerListingResponse {
        answers = List.copyOf(answers);
    }

    public static AnswerListingResponse from(List<AnswerCard> cards, int limit) {
        List<Answer> mapped = cards.stream().map(Answer::from).toList();
        Cursor next = cards.size() == limit
                ? new Cursor(cards.getLast().publishedAt(), cards.getLast().answerId())
                : null;
        return new AnswerListingResponse(mapped, next);
    }

    public record Answer(
            long answerId,
            String authorNickname,
            String authorCoarseRegionCode,
            String bodyText,
            List<Long> mediaIds,
            BigDecimal bearingFromSenderDegrees,
            Long distanceM,
            String distanceBand,
            Instant publishedAt,
            Instant editedAt,
            boolean reactedByMe,
            long reactionCount
    ) {
        public Answer {
            mediaIds = List.copyOf(mediaIds);
        }

        public static Answer from(AnswerCard card) {
            return new Answer(
                    card.answerId(), card.authorNickname(), card.authorCoarseRegionCode(), card.bodyText(),
                    card.mediaIds(), card.bearingFromSenderDegrees(), card.distanceM(), card.distanceBand(),
                    card.publishedAt(), card.editedAt(), card.reactedByMe(), card.reactionCount());
        }
    }

    @Schema(name = "AnswerCursor")
    public record Cursor(Instant publishedAt, long answerId) {
    }
}
