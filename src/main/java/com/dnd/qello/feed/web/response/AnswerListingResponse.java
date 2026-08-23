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
        @Schema(description = "그 질문글의 답변 목록. 열람 자격이 없으면 빈 목록입니다") List<Answer> answers,
        @Schema(description = "다음 페이지 커서. 마지막 페이지면 null입니다") Cursor nextCursor
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

    @Schema(name = "AnswerCard")
    public record Answer(
            @Schema(description = "답변 식별자") long answerId,
            @Schema(description = "답변 작성자의 닉네임") String authorNickname,
            @Schema(description = "답변 작성자의 대략적인 지역 코드") String authorCoarseRegionCode,
            @Schema(description = "답변 본문") String bodyText,
            @Schema(description = "첨부된 이미지 식별자 목록") List<Long> mediaIds,
            @Schema(description = "발신자 기준으로 계산한 방위각(도 단위)") BigDecimal bearingFromSenderDegrees,
            @Schema(description = "발신자와의 거리(미터). 근거리 구간에서는 null이고 대신 distanceBand가 채워집니다") Long distanceM,
            @Schema(description = "근거리 구간일 때만 채워지는 거리 표시 문구. 그 외에는 null이고 대신 distanceM이 채워집니다") String distanceBand,
            @Schema(description = "이 답변이 공개된 시각") Instant publishedAt,
            @Schema(description = "이 답변을 마지막으로 수정한 시각. 수정한 적이 없으면 null입니다") Instant editedAt,
            @Schema(description = "조회하는 본인이 이 답변에 공감했는지 여부") boolean reactedByMe,
            @Schema(description = "이 답변이 받은 공감 총수") long reactionCount
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
    public record Cursor(
            @Schema(description = "다음 페이지 조회에 쓸 공개 시각") Instant publishedAt,
            @Schema(description = "다음 페이지 조회에 쓸 답변 식별자") long answerId
    ) {
    }
}
