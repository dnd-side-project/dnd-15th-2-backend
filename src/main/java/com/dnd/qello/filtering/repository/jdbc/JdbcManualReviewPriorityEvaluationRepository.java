package com.dnd.qello.filtering.repository.jdbc;

import java.sql.Timestamp;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.dnd.qello.filtering.domain.ManualReviewPriorityEvaluation;
import com.dnd.qello.filtering.repository.ManualReviewPriorityEvaluationRepository;

@Repository
public class JdbcManualReviewPriorityEvaluationRepository implements ManualReviewPriorityEvaluationRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcManualReviewPriorityEvaluationRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public ManualReviewPriorityEvaluation save(ManualReviewPriorityEvaluation evaluation) {
		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
		jdbc.update("""
			INSERT INTO manual_review_priority_evaluation
				(manual_review_case_id, band, reason_code, policy_version, evaluated_at)
			VALUES (:manualReviewCaseId, :band, :reasonCode, :policyVersion, :evaluatedAt)
			""",
			new MapSqlParameterSource()
				.addValue("manualReviewCaseId", evaluation.manualReviewCaseId())
				.addValue("band", evaluation.band().name())
				.addValue("reasonCode", evaluation.reasonCode().name())
				.addValue("policyVersion", evaluation.policyVersion())
				.addValue("evaluatedAt", Timestamp.from(evaluation.evaluatedAt())),
			keyHolder, new String[] {"id"});
		return new ManualReviewPriorityEvaluation(keyHolder.getKey().longValue(), evaluation.manualReviewCaseId(),
			evaluation.band(), evaluation.reasonCode(), evaluation.policyVersion(), evaluation.evaluatedAt());
	}
}
