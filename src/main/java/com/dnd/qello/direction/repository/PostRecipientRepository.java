package com.dnd.qello.direction.repository;

import java.util.List;

import com.dnd.qello.direction.domain.PostRecipient;

public interface PostRecipientRepository {
	PostRecipient save(PostRecipient recipient);
	List<PostRecipient> findAllByPostId(long postId);
}
