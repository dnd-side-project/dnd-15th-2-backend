package com.dnd.qello.safety.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.safety.domain.ModerationReview;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportStatus;
import com.dnd.qello.safety.domain.UserBlock;
import com.dnd.qello.safety.repository.SafetyRepository;

/** 차단·신고·검토 행의 원자적 상태 변경만 담당한다. 운영자 API는 별도 기능이다. */
@Service
public class SafetyService {

	private final SafetyRepository repository;

	public SafetyService(SafetyRepository repository) { this.repository = repository; }

	@Transactional
	public UserBlock block(long blockerId, long blockedId, Instant at) {
		return repository.block(UserBlock.create(blockerId, blockedId, at));
	}

	@Transactional
	public UserBlock releaseBlock(long blockerId, long blockedId, Instant at) {
		return repository.releaseBlock(blockerId, blockedId, at);
	}

	@Transactional
	public Report report(Report report) {
		return repository.findOpenReport(report.reporterId(), report.targetUserId(), report.directionPostId(), report.answerId())
			.orElseGet(() -> repository.saveReport(report));
	}

	@Transactional
	public Report startReview(long reportId) {
		Report report = loadReport(reportId);
		return repository.updateReport(report.startReview());
	}

	@Transactional
	public Report resolve(Report report, ReportStatus status, Instant at) {
		return repository.updateReport(report.resolve(status, at));
	}

	@Transactional
	public ModerationReview review(ModerationReview review) {
		return repository.saveReview(review);
	}

	@Transactional
	public Report reviewAndResolve(Report report, ModerationReview review, ReportStatus nextStatus, Instant resolvedAt) {
		repository.saveReview(review);
		return repository.updateReport(report.resolve(nextStatus, resolvedAt));
	}

	private Report loadReport(long reportId) {
		return repository.findReportById(reportId)
			.orElseThrow(() -> new IllegalArgumentException("신고를 찾을 수 없습니다: " + reportId));
	}
}
