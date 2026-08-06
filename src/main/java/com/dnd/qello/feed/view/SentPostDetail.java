package com.dnd.qello.feed.view;

import java.time.Instant;

public record SentPostDetail(
	SentPostCard card,
	Instant answersReadAt
) { }
