package com.dnd.qello.notification.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.notification.config.PushPolicyProperties;
import com.dnd.qello.notification.domain.DeliveryStatus;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.NotificationPreferenceSnapshot;
import com.dnd.qello.notification.domain.NotificationQuietHours;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.PushDevice;
import com.dnd.qello.notification.domain.PushDeviceStatus;
import com.dnd.qello.notification.domain.PushPlatform;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.push.PushDeliveryTerminalResult;
import com.dnd.qello.notification.push.PushDispatchContext;
import com.dnd.qello.notification.push.group.ClaimedPushDeviceDispatch;
import com.dnd.qello.notification.push.group.ClaimedPushDispatchGroup;
import com.dnd.qello.notification.push.group.PushBudgetReservation;
import com.dnd.qello.notification.push.group.PushDispatchGroup;
import com.dnd.qello.notification.push.group.PushDispatchGroupContext;
import com.dnd.qello.notification.push.group.PushDispatchGroupStatus;
import com.dnd.qello.notification.push.group.PushDispatchMemberContext;
import com.dnd.qello.notification.push.group.PushGroupingCandidate;
import com.dnd.qello.notification.push.policy.PushBudgetPolicy;
import com.dnd.qello.notification.repository.PushDispatchGroupRepository;
import com.dnd.qello.notification.repository.jdbc.sql.PushDispatchGroupSql;

@Repository
public class JdbcPushDispatchGroupRepository implements PushDispatchGroupRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcPushDispatchGroupRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<PushGroupingCandidate> lockUngrouped(int limit, Instant at) {
		if (limit <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "limit",
				"limit은 양수여야 합니다.");
		}
		if (at == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "at",
				"편입 시각은 필수입니다.");
		}
		return jdbc.query(PushDispatchGroupSql.LOCK_UNGROUPED,
			new MapSqlParameterSource().addValue("limit", limit).addValue("at", timestamp(at)),
			(rs, row) -> mapCandidate(rs));
	}

	@Override
	public void acquireGroupingLock(long recipientId, NotificationType type) {
		validateRecipientAndType(recipientId, type);
		jdbc.query(PushDispatchGroupSql.ACQUIRE_GROUPING_LOCK,
			new MapSqlParameterSource("groupingKey", recipientId + ":" + type.name()),
			(ResultSet rs) -> null);
	}

	@Override
	public int closeExpiredCollecting(long recipientId, NotificationType type, Instant at) {
		validateRecipientAndType(recipientId, type);
		requireAt(at);
		return jdbc.update(PushDispatchGroupSql.CLOSE_EXPIRED_COLLECTING,
			new MapSqlParameterSource()
				.addValue("recipientId", recipientId)
				.addValue("notificationType", type.name())
				.addValue("at", timestamp(at)));
	}

	@Override
	public Optional<PushDispatchGroup> findCollectingForUpdate(
		long recipientId, NotificationType type, Instant at) {
		validateRecipientAndType(recipientId, type);
		requireAt(at);
		return jdbc.query(PushDispatchGroupSql.FIND_COLLECTING_FOR_UPDATE,
			new MapSqlParameterSource()
				.addValue("recipientId", recipientId)
				.addValue("notificationType", type.name())
				.addValue("at", timestamp(at)),
			(rs, row) -> mapGroup(rs)).stream().findFirst();
	}

	@Override
	public PushDispatchGroup save(PushDispatchGroup group) {
		if (group == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "group",
				"group은 필수입니다.");
		}
		if (group.status() == PushDispatchGroupStatus.COLLECTING) {
			return insertWindowed(group);
		}
		return jdbc.queryForObject(PushDispatchGroupSql.UPSERT_GROUP, groupParams(group),
			(rs, row) -> mapGroup(rs));
	}

	private PushDispatchGroup insertWindowed(PushDispatchGroup group) {
		jdbc.getJdbcTemplate().execute("SAVEPOINT windowed_group_insert");
		try {
			return jdbc.queryForObject(PushDispatchGroupSql.INSERT_GROUP, groupParams(group),
				(rs, row) -> mapGroup(rs));
		} catch (DuplicateKeyException exception) {
			jdbc.getJdbcTemplate().execute("ROLLBACK TO SAVEPOINT windowed_group_insert");
			Optional<PushDispatchGroup> open = findCollectingForUpdate(
				group.recipientId(), group.notificationType(), group.createdAt());
			if (open.isPresent()) {
				return open.get();
			}
			if (!causedByConstraint(exception, "uq_push_dispatch_group_aggregation_key")) {
				throw exception;
			}
			jdbc.getJdbcTemplate().execute("SAVEPOINT windowed_group_insert_retry");
			try {
				return jdbc.queryForObject(PushDispatchGroupSql.INSERT_GROUP,
					groupParams(withUniqueAggregationKey(group)), (rs, row) -> mapGroup(rs));
			} catch (DuplicateKeyException retry) {
				jdbc.getJdbcTemplate().execute("ROLLBACK TO SAVEPOINT windowed_group_insert_retry");
				throw retry;
			}
		}
	}

	@Override
	public boolean addMember(long groupId, long notificationId, Instant at) {
		if (groupId <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_ID, "groupId",
				"groupId는 양수여야 합니다.");
		}
		if (notificationId <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_ID, "notificationId",
				"notificationId는 양수여야 합니다.");
		}
		requireAt(at);
		try {
			return jdbc.update(PushDispatchGroupSql.INSERT_MEMBER,
				new MapSqlParameterSource()
					.addValue("groupId", groupId)
					.addValue("notificationId", notificationId)
					.addValue("at", timestamp(at))) == 1;
		} catch (DuplicateKeyException exception) {
			return false;
		}
	}

	@Override
	public List<ClaimedPushDispatchGroup> claimDueGroups(int limit, Instant now, Instant leaseUntil) {
		if (limit <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "limit",
				"limit은 양수여야 합니다.");
		}
		if (now == null || leaseUntil == null || !leaseUntil.isAfter(now)) {
			throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "leaseUntil",
				"현재 시각 이후의 leaseUntil이 필요합니다.");
		}
		return jdbc.query(PushDispatchGroupSql.CLAIM_DUE_GROUPS,
			new MapSqlParameterSource()
				.addValue("limit", limit)
				.addValue("now", timestamp(now))
				.addValue("leaseUntil", timestamp(leaseUntil)),
			(rs, row) -> new ClaimedPushDispatchGroup(
				rs.getLong("group_id"), rs.getInt("generation"), instant(rs, "lease_until")));
	}

	@Override
	public boolean transition(long groupId, int generation, PushDispatchGroupStatus status,
		Instant nextAttemptAt, Instant completedAt, Instant at) {
		if (groupId <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_ID, "groupId",
				"groupId는 양수여야 합니다.");
		}
		if (generation <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "generation",
				"generation은 양수여야 합니다.");
		}
		if (status == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "status",
				"group 상태는 필수입니다.");
		}
		if (nextAttemptAt == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "nextAttemptAt",
				"다음 시도 시각은 필수입니다.");
		}
		requireAt(at);
		if (status.isTerminal() != (completedAt != null)) {
			throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "completedAt",
				"terminal 상태와 completedAt은 함께 존재해야 합니다.");
		}
		return jdbc.update(PushDispatchGroupSql.TRANSITION_GROUP,
			new MapSqlParameterSource()
				.addValue("groupId", groupId)
				.addValue("generation", generation)
				.addValue("status", status.name())
				.addValue("nextAttemptAt", timestamp(nextAttemptAt))
				.addValue("completedAt", timestamp(completedAt))) == 1;
	}

	@Override
	public Optional<PushDispatchGroupContext> findGroupContext(long groupId, int generation, Instant at) {
		requireGroupId(groupId);
		requireGeneration(generation);
		requireAt(at);
		return Optional.ofNullable(jdbc.query(PushDispatchGroupSql.FIND_GROUP_CONTEXT,
			new MapSqlParameterSource()
				.addValue("groupId", groupId)
				.addValue("generation", generation)
				.addValue("at", timestamp(at)),
			this::extractGroupContext));
	}

	@Override
	public boolean cancelGroup(long groupId, int generation, Instant at) {
		requireGroupId(groupId);
		requireGeneration(generation);
		requireAt(at);
		Boolean cancelled = jdbc.queryForObject(PushDispatchGroupSql.CANCEL_GROUP,
			new MapSqlParameterSource()
				.addValue("groupId", groupId)
				.addValue("generation", generation)
				.addValue("at", timestamp(at)),
			Boolean.class);
		return Boolean.TRUE.equals(cancelled);
	}

	@Override
	public PushBudgetReservation reserveBudget(
		long groupId, int generation, LocalDate budgetDate,
		NotificationType type, PushPolicyProperties properties, Instant at) {
		requireGroupId(groupId);
		requireGeneration(generation);
		if (budgetDate == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "budgetDate",
				"budgetDate는 필수입니다.");
		}
		if (type == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "type",
				"notification type은 필수입니다.");
		}
		if (properties == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "properties",
				"push 정책 설정은 필수입니다.");
		}
		requireAt(at);
		Optional<PushDispatchGroup> locked = jdbc.query(PushDispatchGroupSql.LOCK_GROUP_FOR_UPDATE,
			new MapSqlParameterSource("groupId", groupId),
			(rs, row) -> mapGroup(rs)).stream().findFirst();
		if (locked.isEmpty() || locked.get().attemptCount() != generation
			|| locked.get().status() != PushDispatchGroupStatus.PROCESSING) {
			return PushBudgetReservation.STALE_GROUP;
		}
		if (locked.get().budgetConsumedAt() != null) {
			return PushBudgetReservation.ALREADY_RESERVED;
		}
		java.sql.Date sqlDate = java.sql.Date.valueOf(budgetDate);
		MapSqlParameterSource budgetParams = new MapSqlParameterSource()
			.addValue("userId", locked.get().recipientId())
			.addValue("budgetDate", sqlDate)
			.addValue("at", timestamp(at));
		jdbc.update(PushDispatchGroupSql.UPSERT_DAILY_BUDGET, budgetParams);
		PushBudgetPolicy.BudgetSnapshot snapshot = jdbc.queryForObject(PushDispatchGroupSql.LOCK_DAILY_BUDGET,
			budgetParams, (rs, row) -> new PushBudgetPolicy.BudgetSnapshot(
				rs.getInt("consumed_total"), rs.getInt("consumed_general")));
		PushBudgetPolicy.Decision decision = new PushBudgetPolicy(properties).decide(snapshot, type);
		if (decision == PushBudgetPolicy.Decision.DENY) {
			return PushBudgetReservation.LIMIT_EXCEEDED;
		}
		int generalIncrement = decision == PushBudgetPolicy.Decision.ALLOW_GENERAL ? 1 : 0;
		jdbc.update(PushDispatchGroupSql.INCREMENT_DAILY_BUDGET,
			budgetParams.addValue("generalIncrement", generalIncrement));
		int stamped = jdbc.update(PushDispatchGroupSql.STAMP_GROUP_BUDGET,
			new MapSqlParameterSource()
				.addValue("groupId", groupId)
				.addValue("generation", generation)
				.addValue("budgetDate", sqlDate)
				.addValue("at", timestamp(at)));
		if (stamped != 1) {
			throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "groupId",
				"예산을 기록하는 동안 group 상태가 바뀌었습니다.");
		}
		return PushBudgetReservation.RESERVED;
	}

	@Override
	public boolean cancelMemberDeliveries(
		long groupId, int generation, Collection<Long> deliveryIds, Instant at) {
		requireGroupId(groupId);
		requireGeneration(generation);
		requireAt(at);
		if (!lockGroupProcessing(groupId, generation)) {
			return false;
		}
		if (deliveryIds == null || deliveryIds.isEmpty()) {
			return true;
		}
		jdbc.update(PushDispatchGroupSql.CANCEL_MEMBER_DELIVERIES,
			new MapSqlParameterSource()
				.addValue("groupId", groupId)
				.addValue("generation", generation)
				.addValue("at", timestamp(at))
				.addValue("deliveryIds", List.copyOf(deliveryIds)));
		return true;
	}

	@Override
	public List<ClaimedPushDeviceDispatch> claimDevices(
		long groupId, int groupGeneration,
		Map<Long, Set<Long>> eligibleDeliveryIdsByDevice,
		Instant now, Instant leaseUntil) {
		requireGroupId(groupId);
		requireGeneration(groupGeneration);
		if (now == null || leaseUntil == null || !leaseUntil.isAfter(now)) {
			throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "leaseUntil",
				"현재 시각 이후의 leaseUntil이 필요합니다.");
		}
		if (eligibleDeliveryIdsByDevice == null || eligibleDeliveryIdsByDevice.isEmpty()) {
			return List.of();
		}
		List<Long> deliveryIds = eligibleDeliveryIdsByDevice.values().stream()
			.flatMap(Collection::stream)
			.distinct()
			.toList();
		if (deliveryIds.isEmpty()) {
			return List.of();
		}
		List<ClaimedRow> rows = jdbc.query(PushDispatchGroupSql.CLAIM_DEVICES,
			new MapSqlParameterSource()
				.addValue("groupId", groupId)
				.addValue("generation", groupGeneration)
				.addValue("deliveryIds", deliveryIds)
				.addValue("now", timestamp(now))
				.addValue("leaseUntil", timestamp(leaseUntil)),
			(rs, row) -> new ClaimedRow(
				rs.getLong("device_id"),
				rs.getLong("delivery_id"),
				rs.getInt("generation"),
				instant(rs, "lease_until")));
		LinkedHashMap<Long, Map<Long, Integer>> generationsByDevice = new LinkedHashMap<>();
		LinkedHashMap<Long, Instant> leaseByDevice = new LinkedHashMap<>();
		for (ClaimedRow row : rows) {
			generationsByDevice.computeIfAbsent(row.deviceId(), key -> new LinkedHashMap<>())
				.put(row.deliveryId(), row.generation());
			leaseByDevice.putIfAbsent(row.deviceId(), row.leaseUntil());
		}
		List<ClaimedPushDeviceDispatch> claimed = new ArrayList<>();
		for (Map.Entry<Long, Map<Long, Integer>> entry : generationsByDevice.entrySet()) {
			claimed.add(new ClaimedPushDeviceDispatch(
				entry.getKey(), entry.getValue(), leaseByDevice.get(entry.getKey())));
		}
		return List.copyOf(claimed);
	}

	@Override
	public boolean completeDevice(
		long groupId, int groupGeneration, long deviceId,
		Map<Long, Integer> deliveryGenerations,
		PushDeliveryTerminalResult result, Instant at,
		Instant nextAttemptAt, String providerMessageId) {
		requireGroupId(groupId);
		requireGeneration(groupGeneration);
		requireDeviceId(deviceId);
		requireAt(at);
		if (result == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "result",
				"device terminal 결과는 필수입니다.");
		}
		if (nextAttemptAt == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "nextAttemptAt",
				"다음 시도 시각은 필수입니다.");
		}
		if (result == PushDeliveryTerminalResult.SENT
			&& (providerMessageId == null || providerMessageId.isBlank())) {
			providerMessageId = null;
		}
		if (deliveryGenerations == null || deliveryGenerations.isEmpty()) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "deliveryGenerations",
				"delivery generation은 필수입니다.");
		}
		if (!lockGroupProcessing(groupId, groupGeneration)) {
			return false;
		}
		int updated = 0;
		for (Map.Entry<Long, Integer> entry : deliveryGenerations.entrySet()) {
			if (entry.getKey() == null || entry.getKey() <= 0
				|| entry.getValue() == null || entry.getValue() <= 0) {
				throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "deliveryGenerations",
					"delivery generation이 올바르지 않습니다.");
			}
			updated += jdbc.update(PushDispatchGroupSql.COMPLETE_DEVICE_DELIVERY,
				new MapSqlParameterSource()
					.addValue("groupId", groupId)
					.addValue("groupGeneration", groupGeneration)
					.addValue("deviceId", deviceId)
					.addValue("deliveryId", entry.getKey())
					.addValue("generation", entry.getValue())
					.addValue("terminalStatus", result.name())
					.addValue("at", timestamp(at))
					.addValue("nextAttemptAt", timestamp(nextAttemptAt))
					.addValue("providerMessageId",
						result == PushDeliveryTerminalResult.SENT ? providerMessageId : null));
		}
		if (updated == 0) {
			return false;
		}
		if (updated != deliveryGenerations.size()) {
			throw new NotificationException(NotificationErrorCode.INVALID_NOTIFICATION_STATE, "deliveryGenerations",
				"device terminal 반영이 일치하지 않습니다.");
		}
		return true;
	}

	@Override
	public PushDispatchGroupStatus finalizeGroup(long groupId, int groupGeneration, Instant at) {
		requireGroupId(groupId);
		requireGeneration(groupGeneration);
		requireAt(at);
		List<String> statuses = jdbc.query(PushDispatchGroupSql.FINALIZE_GROUP,
			new MapSqlParameterSource()
				.addValue("groupId", groupId)
				.addValue("generation", groupGeneration)
				.addValue("at", timestamp(at)),
			(rs, row) -> rs.getString("status"));
		if (statuses.isEmpty()) {
			throw new NotificationException(NotificationErrorCode.INVALID_NOTIFICATION_STATUS, "groupId",
				"현재 group 상태로는 종료할 수 없습니다.");
		}
		return PushDispatchGroupStatus.valueOf(statuses.getFirst());
	}

	@Override
	public boolean invalidateClaimedDevice(
		long groupId, int groupGeneration, long deviceId,
		Map<Long, Integer> deliveryGenerations, Instant at) {
		requireGroupId(groupId);
		requireGeneration(groupGeneration);
		requireDeviceId(deviceId);
		requireAt(at);
		if (deliveryGenerations == null || deliveryGenerations.isEmpty()) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "deliveryGenerations",
				"delivery generation은 필수입니다.");
		}
		if (!lockGroupProcessing(groupId, groupGeneration)) {
			return false;
		}
		int dead = 0;
		for (Map.Entry<Long, Integer> entry : deliveryGenerations.entrySet()) {
			dead += jdbc.update(PushDispatchGroupSql.COMPLETE_DEVICE_DELIVERY,
				new MapSqlParameterSource()
					.addValue("groupId", groupId)
					.addValue("groupGeneration", groupGeneration)
					.addValue("deviceId", deviceId)
					.addValue("deliveryId", entry.getKey())
					.addValue("generation", entry.getValue())
					.addValue("terminalStatus", PushDeliveryTerminalResult.DEAD.name())
					.addValue("at", timestamp(at))
					.addValue("nextAttemptAt", timestamp(at))
					.addValue("providerMessageId", null));
		}
		if (dead == 0) {
			return false;
		}
		if (dead != deliveryGenerations.size()) {
			throw new NotificationException(NotificationErrorCode.INVALID_NOTIFICATION_STATE, "deliveryGenerations",
				"invalid token 반영이 일치하지 않습니다.");
		}
		jdbc.update(PushDispatchGroupSql.INVALIDATE_DEVICE_IF_ACTIVE,
			new MapSqlParameterSource("deviceId", deviceId));
		jdbc.update(PushDispatchGroupSql.CANCEL_DEVICE_PENDING_OR_FAILED,
			new MapSqlParameterSource()
				.addValue("deviceId", deviceId)
				.addValue("at", timestamp(at)));
		return true;
	}

	private boolean lockGroupProcessing(long groupId, int generation) {
		return jdbc.query(PushDispatchGroupSql.LOCK_GROUP_PROCESSING,
			new MapSqlParameterSource()
				.addValue("groupId", groupId)
				.addValue("generation", generation),
			(rs, row) -> rs.getLong("id")).stream().findFirst().isPresent();
	}

	private static void requireDeviceId(long deviceId) {
		if (deviceId <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_ID, "deviceId",
				"deviceId는 양수여야 합니다.");
		}
	}

	private record ClaimedRow(long deviceId, long deliveryId, int generation, Instant leaseUntil) {
	}

	private PushDispatchGroupContext extractGroupContext(ResultSet rs) throws SQLException {
		if (!rs.next()) {
			return null;
		}
		PushDispatchGroup group = mapContextGroup(rs);
		ZoneId budgetZone = ZoneId.of(rs.getString("budget_zone"));
		NotificationPreferenceSnapshot preference = mapPreference(rs, group.recipientId());
		Instant lastRecommendationAttemptAt = instant(rs, "last_recommendation_attempt_at");
		List<PushDispatchMemberContext> members = new ArrayList<>();
		do {
			Long deliveryId = nullableLong(rs, "delivery_id");
			if (deliveryId == null) {
				continue;
			}
			members.add(new PushDispatchMemberContext(deliveryId, mapPushDispatchContext(rs)));
		} while (rs.next());
		return new PushDispatchGroupContext(
			group, budgetZone, preference, lastRecommendationAttemptAt, members);
	}

	private static PushDispatchGroup withUniqueAggregationKey(PushDispatchGroup group) {
		return new PushDispatchGroup(
			null,
			group.recipientId(),
			group.notificationType(),
			group.aggregationKey() + ":" + Long.toUnsignedString(System.nanoTime()),
			group.status(),
			group.windowStartedAt(),
			group.collectUntil(),
			group.policyExpiresAt(),
			group.attemptCount(),
			group.nextAttemptAt(),
			group.budgetLocalDate(),
			group.budgetConsumedAt(),
			group.firstAttemptedAt(),
			group.createdAt(),
			group.completedAt());
	}

	private static boolean causedByConstraint(DuplicateKeyException exception, String constraint) {
		String message = exception.getMostSpecificCause().getMessage();
		return message != null && message.contains(constraint);
	}

	private static MapSqlParameterSource groupParams(PushDispatchGroup group) {
		return new MapSqlParameterSource()
			.addValue("recipientId", group.recipientId())
			.addValue("notificationType", group.notificationType().name())
			.addValue("aggregationKey", group.aggregationKey())
			.addValue("status", group.status().name())
			.addValue("windowStartedAt", timestamp(group.windowStartedAt()))
			.addValue("collectUntil", timestamp(group.collectUntil()))
			.addValue("policyExpiresAt", timestamp(group.policyExpiresAt()))
			.addValue("attemptCount", group.attemptCount())
			.addValue("nextAttemptAt", timestamp(group.nextAttemptAt()))
			.addValue("budgetLocalDate", group.budgetLocalDate())
			.addValue("budgetConsumedAt", timestamp(group.budgetConsumedAt()))
			.addValue("firstAttemptedAt", timestamp(group.firstAttemptedAt()))
			.addValue("createdAt", timestamp(group.createdAt()))
			.addValue("completedAt", timestamp(group.completedAt()));
	}

	private static PushGroupingCandidate mapCandidate(ResultSet rs) throws SQLException {
		return new PushGroupingCandidate(
			rs.getLong("notification_id"),
			rs.getLong("recipient_id"),
			NotificationType.valueOf(rs.getString("notification_type")),
			instant(rs, "created_at"),
			nullableLong(rs, "recommendation_cycle_id"));
	}

	private static PushDispatchGroup mapGroup(ResultSet rs) throws SQLException {
		return mapGroup(rs, "notification_type");
	}

	private static PushDispatchGroup mapContextGroup(ResultSet rs) throws SQLException {
		return mapGroup(rs, "group_notification_type");
	}

	private static PushDispatchGroup mapGroup(ResultSet rs, String typeColumn) throws SQLException {
		return new PushDispatchGroup(
			rs.getLong("id"),
			rs.getLong("recipient_id"),
			NotificationType.valueOf(rs.getString(typeColumn)),
			rs.getString("aggregation_key"),
			PushDispatchGroupStatus.valueOf(rs.getString("status")),
			instant(rs, "window_started_at"),
			instant(rs, "collect_until"),
			instant(rs, "policy_expires_at"),
			rs.getInt("attempt_count"),
			instant(rs, "next_attempt_at"),
			localDate(rs, "budget_local_date"),
			instant(rs, "budget_consumed_at"),
			instant(rs, "first_attempted_at"),
			instant(rs, "created_at"),
			instant(rs, "completed_at"));
	}

	private static NotificationPreferenceSnapshot mapPreference(ResultSet rs, long userId) throws SQLException {
		EnumMap<NotificationType, Boolean> typeEnabled = new EnumMap<>(NotificationType.class);
		typeEnabled.put(NotificationType.ANSWER_RECEIVED, rs.getBoolean("type_answer_received"));
		typeEnabled.put(NotificationType.ANSWER_REACTED, rs.getBoolean("type_answer_reacted"));
		typeEnabled.put(NotificationType.DIRECTION_POST_RECEIVED, rs.getBoolean("type_direction_post_received"));
		typeEnabled.put(NotificationType.REPORT_RESOLVED, rs.getBoolean("type_report_resolved"));
		typeEnabled.put(NotificationType.QUESTION_PROPOSAL_REVIEWED, rs.getBoolean("type_question_proposal_reviewed"));
		typeEnabled.put(NotificationType.QUESTION_RECOMMENDED, rs.getBoolean("type_question_recommended"));
		return new NotificationPreferenceSnapshot(userId, rs.getBoolean("push_enabled"), quietHours(rs), typeEnabled);
	}

	private static NotificationQuietHours quietHours(ResultSet rs) throws SQLException {
		LocalTime quietStart = toLocalTime(rs.getTime("quiet_start"));
		LocalTime quietEnd = toLocalTime(rs.getTime("quiet_end"));
		String quietZoneId = rs.getString("quiet_zone_id");
		if (quietStart == null && quietEnd == null && quietZoneId == null) {
			return null;
		}
		return new NotificationQuietHours(quietStart, quietEnd, ZoneId.of(quietZoneId));
	}

	private static PushDispatchContext mapPushDispatchContext(ResultSet rs) throws SQLException {
		NotificationDelivery delivery = new NotificationDelivery(
			nullableLong(rs, "delivery_id"),
			rs.getLong("delivery_notification_id"),
			rs.getLong("delivery_push_device_id"),
			DeliveryStatus.valueOf(rs.getString("delivery_status")),
			rs.getInt("delivery_attempt_count"),
			instant(rs, "delivery_next_attempt_at"),
			instant(rs, "delivery_created_at"),
			instant(rs, "delivery_sent_at"),
			rs.getString("delivery_provider_message_id"));
		Notification notification = new Notification(
			nullableLong(rs, "notification_id"),
			rs.getLong("notification_recipient_id"),
			rs.getLong("notification_outbox_event_id"),
			NotificationType.valueOf(rs.getString("notification_type")),
			rs.getString("notification_dedup_key"),
			nullableLong(rs, "notification_direction_post_id"),
			nullableLong(rs, "notification_answer_id"),
			nullableLong(rs, "notification_report_id"),
			NotificationStatus.valueOf(rs.getString("notification_status")),
			instant(rs, "notification_created_at"),
			instant(rs, "notification_read_at"));
		PushDevice device = new PushDevice(
			nullableLong(rs, "device_id"),
			rs.getLong("device_user_id"),
			PushPlatform.valueOf(rs.getString("device_platform")),
			rs.getBytes("device_token_ciphertext"),
			rs.getString("device_token_fingerprint"),
			PushDeviceStatus.valueOf(rs.getString("device_status")),
			instant(rs, "device_last_seen_at"),
			instant(rs, "device_revoked_at"));
		return new PushDispatchContext(
			delivery,
			notification,
			device,
			nullableLong(rs, "actor_id"),
			new PushDispatchContext.DispatchValiditySnapshot(
				rs.getBoolean("recipient_active"),
				rs.getBoolean("actor_active"),
				rs.getBoolean("preference_enabled"),
				rs.getBoolean("blocked_in_either_direction"),
				rs.getBoolean("target_valid"),
				rs.getBoolean("has_remaining_time")));
	}

	private static void validateRecipientAndType(long recipientId, NotificationType type) {
		if (recipientId <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_ID, "recipientId",
				"recipientId는 양수여야 합니다.");
		}
		if (type == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "type",
				"notification type은 필수입니다.");
		}
	}

	private static void requireAt(Instant at) {
		if (at == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "at",
				"처리 시각은 필수입니다.");
		}
	}

	private static void requireGroupId(long groupId) {
		if (groupId <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_ID, "groupId",
				"groupId는 양수여야 합니다.");
		}
	}

	private static void requireGeneration(int generation) {
		if (generation <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "generation",
				"generation은 양수여야 합니다.");
		}
	}

	private static LocalTime toLocalTime(Time value) {
		return value == null ? null : value.toLocalTime();
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
		java.sql.Date value = rs.getDate(column);
		return value == null ? null : value.toLocalDate();
	}

	private static Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}
}
