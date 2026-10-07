package com.dnd.qello.direction.service;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.moderation.ModerationVerdictApplier;

import lombok.RequiredArgsConstructor;

/**
 * 질문글 텍스트 moderation 판정을 `moderation_status`에 반영한다(#137).
 *
 * <p>
 * 매칭 워커와 같은 질문글 행 잠금(FOR UPDATE)을 잡아, 판정 반영과 매칭 판단이 서로의 중간 상태를 보지 않게 한다. 전이 가능
 * 여부는 {@link DirectionPost}가 정하고, 전이가 없으면 저장하지 않는다. 질문글이 없으면 재시도로 해소되지 않으므로 상태
 * 변경 없이 끝낸다.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DirectionPostModerationVerdictApplier implements ModerationVerdictApplier {

	private final DirectionPostRepository postRepository;

	@Override
	public FilterTargetType targetType() {
		return FilterTargetType.DIRECTION_POST;
	}

	@Override
	@Transactional
	public void applyVerdict(long targetId, FilterVerdict verdict, Instant at) {
		transition(targetId, post -> verdict == FilterVerdict.ALLOW
				? post.passModeration(at)
				: post.rejectModeration(at));
	}

	@Override
	@Transactional
	public void applyDeadlineElapsed(long targetId, Instant at) {
		transition(targetId, post -> post.holdModerationForReview(at));
	}

	private void transition(long postId, Function<DirectionPost, Optional<DirectionPost>> next) {
		postRepository.findByIdForUpdate(postId).flatMap(next).ifPresent(postRepository::save);
	}
}
