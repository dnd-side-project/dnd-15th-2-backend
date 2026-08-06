package com.dnd.qello.answer.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.domain.MediaAsset;
import com.dnd.qello.answer.domain.MediaAssetStatus;
import com.dnd.qello.answer.domain.MediaAttachment;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.answer.repository.MediaAssetRepository;
import com.dnd.qello.answer.repository.MediaAttachmentRepository;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionPostStatus;
import com.dnd.qello.direction.repository.DirectionPostRepository;

import lombok.RequiredArgsConstructor;

/**
 * 질문글/답변에 READY 미디어를 첨부·해제한다. DB의 deferred content trigger가 최종 방어선이지만
 * (커밋 시점에만 발동, 원인이 뭉뚱그려짐) 이 서비스가 같은 규칙을 먼저 검증해 빠르고 명확한
 * 오류 코드를 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class MediaAttachmentService {

	private final MediaAssetRepository mediaAssetRepository;
	private final MediaAttachmentRepository mediaAttachmentRepository;
	private final DirectionPostRepository directionPostRepository;
	private final AnswerRepository answerRepository;

	@Transactional
	public MediaAttachment attach(AttachCommand command) {
		MediaAsset asset = mediaAssetRepository.findByIdAndOwnerId(command.mediaId(), command.requesterId())
			.orElseThrow(() -> new AnswerException(AnswerErrorCode.MEDIA_NOT_FOUND, "mediaId", "미디어를 찾을 수 없습니다"));
		if (asset.getStatus() != MediaAssetStatus.READY) {
			throw new AnswerException(
				AnswerErrorCode.INVALID_MEDIA_STATUS, "status", "READY 상태의 미디어만 첨부할 수 있습니다");
		}

		if (command.postId() != null) {
			directionPostRepository.findByIdAndSenderId(command.postId(), command.requesterId())
				.orElseThrow(() -> new AnswerException(
					AnswerErrorCode.MEDIA_OWNER_MISMATCH, "postId", "본인 질문글에만 첨부할 수 있습니다"));
		} else {
			answerRepository.findByIdAndAuthorId(command.answerId(), command.requesterId())
				.orElseThrow(() -> new AnswerException(
					AnswerErrorCode.MEDIA_OWNER_MISMATCH, "answerId", "본인 답변에만 첨부할 수 있습니다"));
		}

		MediaAttachment attachment = new MediaAttachment(
			command.mediaId(), command.requesterId(), command.postId(), command.answerId(), command.displayOrder());
		return mediaAttachmentRepository.save(attachment);
	}

	@Transactional
	public void detach(long mediaId, long requesterId) {
		MediaAttachment attachment = mediaAttachmentRepository.findByMediaIdAndOwnerId(mediaId, requesterId)
			.orElseThrow(() -> new AnswerException(AnswerErrorCode.MEDIA_NOT_FOUND, "mediaId", "첨부를 찾을 수 없습니다"));
		assertDetachPreservesContent(attachment);
		mediaAttachmentRepository.deleteByMediaId(mediaId);
	}

	private void assertDetachPreservesContent(MediaAttachment attachment) {
		if (attachment.postId() != null) {
			DirectionPost post = directionPostRepository.findById(attachment.postId())
				.orElseThrow(() -> new AnswerException(AnswerErrorCode.MEDIA_NOT_FOUND, "postId", "질문글을 찾을 수 없습니다"));
			boolean contentRequired = post.getStatus() == DirectionPostStatus.ACTIVE;
			boolean hasBody = hasText(post.getBodyText());
			if (contentRequired && !hasBody
				&& !mediaAttachmentRepository.existsOtherReadyMediaForPost(attachment.postId(), attachment.mediaId())) {
				throw new AnswerException(
					AnswerErrorCode.MEDIA_CONTENT_REQUIRED, "postId", "본문 또는 다른 미디어가 있어야 첨부를 해제할 수 있습니다");
			}
			return;
		}

		Answer answer = answerRepository.findById(attachment.answerId())
			.orElseThrow(() -> new AnswerException(AnswerErrorCode.MEDIA_NOT_FOUND, "answerId", "답변을 찾을 수 없습니다"));
		boolean contentRequired = answer.getStatus() == AnswerStatus.PUBLISHED;
		boolean hasBody = hasText(answer.getBodyText());
		if (contentRequired && !hasBody
			&& !mediaAttachmentRepository.existsOtherReadyMediaForAnswer(attachment.answerId(), attachment.mediaId())) {
			throw new AnswerException(
				AnswerErrorCode.MEDIA_CONTENT_REQUIRED, "answerId", "본문 또는 다른 미디어가 있어야 첨부를 해제할 수 있습니다");
		}
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public record AttachCommand(long requesterId, long mediaId, Long postId, Long answerId, int displayOrder) {
	}
}
