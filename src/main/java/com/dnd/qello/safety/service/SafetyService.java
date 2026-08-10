package com.dnd.qello.safety.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.direction.service.ReceiveSlotReleaseService;
import com.dnd.qello.safety.domain.ModerationReview;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportStatus;
import com.dnd.qello.safety.domain.UserBlock;
import com.dnd.qello.safety.repository.SafetyRepository;

/** 차단·신고·검토 행의 원자적 상태 변경만 담당한다. 운영자 API는 별도 기능이다. */
@Service
public class SafetyService {

	private final SafetyRepository repository;
	private final ReceiveSlotReleaseService receiveSlotReleaseService;

	public SafetyService(SafetyRepository repository, ReceiveSlotReleaseService receiveSlotReleaseService) {
		this.repository = repository;
		this.receiveSlotReleaseService = receiveSlotReleaseService;
	}

	/**
	 * 차단 성립과 동시에, 차단한 사람 자신이 보유한 차단당한 발신자의 미종결 수신
	 * 항목을 같은 트랜잭션에서 BLOCKED로 전이시켜 슬롯을 해제한다(#93). 후보 조회와
	 * 반복 전이는 ReceiveSlotReleaseService.blockAllPendingFor가 소유한다 — 저장
	 * 순서는 중요하지 않다. 둘 다 이 트랜잭션 커밋 전까지는 서로 보이지 않는다.
	 */
	@Transactional
	public UserBlock block(long blockerId, long blockedId, Instant at) {
		UserBlock saved = repository.block(UserBlock.create(blockerId, blockedId, at));
		receiveSlotReleaseService.blockAllPendingFor(blockerId, blockedId, at);
		return saved;
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
