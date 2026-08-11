package com.dnd.qello.filtering.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataFilterJobStatusHistoryRepository extends JpaRepository<FilterJobStatusHistoryJpaEntity, Long> {

	List<FilterJobStatusHistoryJpaEntity> findByFilterJobId(long filterJobId);
}
