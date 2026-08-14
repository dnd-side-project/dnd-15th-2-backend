package com.dnd.qello.answer.repository;

import java.util.List;
import java.util.Optional;

import com.dnd.qello.answer.domain.MediaAttachment;

public interface MediaAttachmentRepository {

	MediaAttachment save(MediaAttachment attachment);

	Optional<MediaAttachment> findByMediaId(long mediaId);

	/** 소유권을 쿼리 조건에 포함한다. 남의 첨부면 빈 결과이며 예외를 던지지 않는다. */
	Optional<MediaAttachment> findByMediaIdAndOwnerId(long mediaId, long ownerId);

	/** 질문글에 저장된 첨부 ID를 display order 순서로 반환한다. */
	List<Long> findMediaIdsByPostId(long postId);

	void deleteByMediaId(long mediaId);

	/** excludingMediaId를 제외하고 이 질문글에 READY 상태로 첨부된 다른 미디어가 있는지 확인한다. */
	boolean existsOtherReadyMediaForPost(long postId, long excludingMediaId);

	/** excludingMediaId를 제외하고 이 답변에 READY 상태로 첨부된 다른 미디어가 있는지 확인한다. */
	boolean existsOtherReadyMediaForAnswer(long answerId, long excludingMediaId);
}
