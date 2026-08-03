package com.dnd.qello.notification.repository;

import java.time.Instant;
import java.util.Optional;

import com.dnd.qello.notification.domain.OutboxEvent;

public interface OutboxEventRepository {

	OutboxEvent save(OutboxEvent event);

	Optional<OutboxEvent> findEventById(long id);

	Optional<OutboxEvent> findByDedupKey(String dedupKey);

	Optional<OutboxEvent> claim(long id, Instant at);

	boolean update(OutboxEvent event);
}
