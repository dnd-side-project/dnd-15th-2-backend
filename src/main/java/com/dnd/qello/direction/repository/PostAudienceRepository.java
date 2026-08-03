package com.dnd.qello.direction.repository;

import java.util.Optional;

import com.dnd.qello.direction.domain.PostAudience;

public interface PostAudienceRepository {
	PostAudience save(PostAudience audience);
	Optional<PostAudience> findByPostId(long postId);
}
