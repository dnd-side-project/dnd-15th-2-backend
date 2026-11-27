/**
 * Created at: 2026-11-25T04:30:47Z
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-PERF-001 through PERF-003
 */
package com.dnd.qello;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.dnd.qello.direction.config.DirectionRecipientSelectionProperties;
import com.dnd.qello.direction.domain.DirectionCandidate;
import com.dnd.qello.direction.domain.RecipientReceiveState;
import com.dnd.qello.direction.matching.DirectionMatchingWorker;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository.DirectionSegmentCandidateCount;
import com.dnd.qello.direction.repository.RecipientReceiveStateRepository;
import com.dnd.qello.direction.repository.jdbc.sql.ActiveUserPresenceSql;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.direction.service.DirectionPreviewResult;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("performance")
abstract class CoordinatePerformanceRunner extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-COORDINATE-PERF-388";
	private static final Instant NOW = Instant.parse("2026-11-24T00:00:00Z");
	private static final Location ORIGIN = new Location(new BigDecimal("37.5"), new BigDecimal("127"));
	private static final long SEED = 38_820_261_124L;
	private static final int WARMUP = 20;
	private static final int MEASUREMENTS = 200;
	private static final long LEASE_NANOS = Duration.ofSeconds(60).toNanos();
	private static final String LOCK_PROTOCOL = "same-sector-prelock-postlock-barriers-v1";
	private static final List<String> SEGMENTS = List.of("N", "NE", "E", "SE", "S", "SW", "W", "NW");
	private static final Map<String, String> BOUNDARIES = Map.of(
			"repository", "ActiveUserPresenceRepository.findCandidates-return",
			"preview", "DirectionPostService.previewAll-return-after-read-transaction",
			"worker", "DirectionMatchingWorker.processBatch-return-after-commit");
	private static final List<String> EXTERNAL_METRICS = List.of("dbCpuNanos", "rowsRead", "wireBytes",
			"transactionNanos", "leaseHeadroomNanos", "lockWaitNanos", "dbBufferIo", "dbTempIo");
	private static final List<String> ENVIRONMENT_FIELDS = List.of("arch", "cpuModel", "hostCores",
			"hostMemoryBytes", "dbCpuAllocation", "jvmCpuAllocation", "dbMemoryLimitBytes",
			"jvmMemoryLimitBytes", "jvmHeapBytes", "kernel", "jdk", "postgres", "postgis",
			"proj", "geographicLib", "imageDigest", "containerSettings", "network", "concurrentLoad",
			"executionOrder");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private NamedParameterJdbcTemplate namedJdbc;
	@Autowired
	private ObjectMapper mapper;
	@Autowired
	private ActiveUserPresenceRepository presenceRepository;
	@Autowired
	private DirectionPostService posts;
	@Autowired
	private DirectionMatchingWorker worker;
	@Autowired
	private DirectionRecipientSelectionProperties selection;
	@Autowired
	private FilterReleaseRegistryService releases;
	@Autowired
	private DirectionMatchingE3MutableClock clock;
	@Autowired
	private ObservedReceiveStates receiveStates;

	private FrozenPostgisOracle oracle;
	private long sender;
	private long question;
	private long scheme;
	private int iteration;
	private boolean workloadCompleted;
	private String verificationOfRawSha256;
	private final List<Sample> samples = new ArrayList<>();
	private final List<Map<String, Object>> summaries = new ArrayList<>();
	private final List<String> blocked = new ArrayList<>();

	abstract StorageAdapter storage();

	interface StorageAdapter {
		String variant();

		void seedPresence(List<Presence> rows);

		void prepareAudience(long postId, Location origin, boolean previousKey);
	}

	record Location(BigDecimal latitude, BigDecimal longitude) {
	}

	record Presence(long userId, Location location, String region, boolean receiveAllowed,
			Instant locationAt, Instant expiresAt, boolean previousKey) {
	}

	private static boolean selected(Workload workload, int concurrency) {
		String condition = System.getProperty("qello.performance.condition");
		return condition == null || condition.equals(workload + "-C" + concurrency);
	}

	private static void configureDatabaseResources() {
		String cpuset = System.getProperty("qello.performance.db-cpuset");
		String memory = System.getProperty("qello.performance.db-memory-bytes");
		assertThat(cpuset).as("BLOCKED: exactly one PostGIS CPU required").matches("[0-9]+");
		assertThat(memory).as("BLOCKED: explicit PostGIS memory limit required").isNotBlank();
		long bytes = Long.parseLong(memory);
		assertThat(bytes).isPositive();
		var docker = postgres.getDockerClient();
		docker.updateContainerCmd(postgres.getContainerId()).withCpusetCpus(cpuset)
				.withMemory(bytes).withMemorySwap(bytes).exec();
		var actual = docker.inspectContainerCmd(postgres.getContainerId()).exec().getHostConfig();
		assertThat(actual.getCpusetCpus()).isEqualTo(cpuset);
		assertThat(actual.getMemory()).isEqualTo(bytes);
		assertThat(actual.getMemorySwap()).as("PostGIS swap disabled").isEqualTo(bytes);
	}

	private boolean mainBaseline() {
		assertThat(storage().variant()).isIn("MAIN_PLAINTEXT_FULL_FLOW", "ENCRYPTED_APP_FULL_FLOW");
		return "MAIN_PLAINTEXT_FULL_FLOW".equals(storage().variant());
	}

	private String activeCommit() {
		String main = requiredCommit("main-commit");
		assertThat(main).isEqualTo("dae144f831224368538f754005c5c66401277e1e");
		return mainBaseline() ? main : requiredCommit("candidate-commit");
	}

	private void assertBaselineEnvironment(JsonNode baseline, JsonNode candidate) {
		assertThat(baseline.path("geographicLib").asText()).isEqualTo("NOT_APPLICABLE");
		var plaintext = baseline.deepCopy();
		var encrypted = candidate.deepCopy();
		((com.fasterxml.jackson.databind.node.ObjectNode) plaintext).remove("geographicLib");
		((com.fasterxml.jackson.databind.node.ObjectNode) encrypted).remove("geographicLib");
		assertThat(plaintext).as("same environment except the candidate-only calculation library")
				.isEqualTo(encrypted);
	}

	private static Path evidenceDirectory() {
		return Path.of(System.getProperty("qello.performance.output-directory", "build/reports/coordinate-encryption"));
	}

	private static Path rawPath(String scenario, String condition) {
		return evidenceDirectory().resolve(scenario + "-" + condition + ".json");
	}

	private void loadMeasurements(String scenario, String condition, JsonNode environment) throws IOException {
		byte[] raw = Files.readAllBytes(rawPath(scenario, condition));
		JsonNode prior = mapper.readTree(raw);
		assertThat(prior.path("workloadCompleted").asBoolean()).as("complete original workload required").isTrue();
		assertThat(prior.path("variant").asText()).isEqualTo(storage().variant());
		assertThat(prior.path("commit").asText()).isEqualTo(activeCommit());
		assertThat(prior.path("scenario").asText()).isEqualTo(scenario);
		assertThat(prior.path("condition").asText()).isEqualTo(condition);
		assertThat(prior.path("environment")).isEqualTo(environment);
		assertThat(prior.path("boundaries")).isEqualTo(mapper.valueToTree(BOUNDARIES));
		assertThat(prior.path("lockProtocol").asText()).isEqualTo(LOCK_PROTOCOL);
		assertThat(prior.path("seed").asLong()).isEqualTo(SEED);
		assertThat(prior.path("warmup").asInt()).isEqualTo(WARMUP);
		assertThat(prior.path("measurements").asInt()).isEqualTo(MEASUREMENTS);
		for (JsonNode sample : prior.path("samples")) {
			samples.add(mapper.treeToValue(sample, Sample.class));
		}
		for (JsonNode summary : prior.path("summaries")) {
			if (!"external-metrics".equals(summary.path("operation").asText())) {
				summaries.add(mapper.convertValue(summary,
						new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
						}));
			}
		}
		verificationOfRawSha256 = sha256(raw);
		workloadCompleted = true;
	}

	private static String sha256(byte[] raw) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}

	private void assertSampleCounts(int concurrency) {
		for (String operation : BOUNDARIES.keySet()) {
			int calls = "worker".equals(operation) ? concurrency : 1;
			assertThat(samples.stream().filter(s -> operation.equals(s.operation()) && "MEASURED".equals(s.phase()))
					.count()).as("complete measured samples: %s", operation).isEqualTo((long) MEASUREMENTS * calls);
			assertThat(samples.stream().filter(s -> operation.equals(s.operation()) && "COLD".equals(s.phase()))
					.count()).as("complete cold samples: %s", operation).isEqualTo((long) calls);
		}
	}

	private void writeBaselineManifest(Map<String, Object> evidence, Path rawFile, byte[] raw) throws IOException {
		Path path = Path.of(System.getProperty("qello.performance.baseline-output",
				evidenceDirectory().resolve("main-baseline.json").toString()));
		var manifest = mapper.createObjectNode();
		for (String field : List.of("variant", "commit", "seed", "warmup", "measurements", "environment",
				"boundaries", "lockProtocol")) {
			manifest.set(field, mapper.valueToTree(evidence.get(field)));
		}
		var conditions = mapper.createObjectNode();
		if (Files.exists(path)) {
			JsonNode prior = mapper.readTree(path.toFile());
			for (var fields = manifest.fieldNames(); fields.hasNext();) {
				String field = fields.next();
				assertThat(prior.path(field)).as("baseline manifest %s", field).isEqualTo(manifest.path(field));
			}
			for (var entries = prior.path("conditions").fields(); entries.hasNext();) {
				var entry = entries.next();
				verifiedRaw(entry.getValue());
				conditions.set(entry.getKey(), entry.getValue());
			}
		}
		var reference = mapper.createObjectNode();
		reference.put("rawFile", rawFile.toAbsolutePath().toString());
		reference.put("rawSha256", sha256(raw));
		var aggregates = mapper.createArrayNode();
		for (var summary : summaries) {
			if (BOUNDARIES.containsKey((String) summary.get("operation"))) {
				aggregates.add(mapper.valueToTree(summary));
			}
		}
		reference.set("summaries", aggregates);
		conditions.set((String) evidence.get("condition"), reference);
		manifest.set("conditions", conditions);
		Files.createDirectories(path.toAbsolutePath().getParent());
		Files.write(path, mapper.writeValueAsBytes(manifest));
	}

	@BeforeEach
	void prepare() {
		configureDatabaseResources();
		clock.setInstant(NOW);
		oracle = new FrozenPostgisOracle(jdbc);
		samples.clear();
		summaries.clear();
		blocked.clear();
		iteration = 0;
		workloadCompleted = false;
		verificationOfRawSha256 = null;
		AnswerModerationReleaseTestFixture.promotedRelease(releases, 1L);
	}

	@AfterEach
	void cleanup() {
		cleanupPosts();
		jdbc.update("DELETE FROM approved_question WHERE approved_by IN"
				+ " (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM active_user_presence WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
	}

	static Stream<Workload> perf001() {
		return workloads(List.of(10_000), List.of(20_100_000L)).filter(w -> selected(w, 1));
	}

	static Stream<Workload> perf002() {
		return workloads(List.of(1_000, 100_000), List.of(10_000L, 100_000L, 20_100_000L))
				.filter(w -> selected(w, 1));
	}

	static Stream<Workload> perf003() {
		return workloads(List.of(10_000), List.of(20_100_000L)).filter(w -> selected(w, 4));
	}

	private static Stream<Workload> workloads(List<Integer> approvedSizes, List<Long> approvedDistances) {
		Set<Integer> selectedSizes = Arrays.stream(System.getProperty("qello.performance.sizes",
				"1000,10000,100000").split(",")).map(Integer::valueOf).collect(Collectors.toSet());
		Set<Long> selectedDistances = Arrays.stream(System.getProperty("qello.performance.distances",
				"10000,100000,20100000").split(",")).map(Long::valueOf).collect(Collectors.toSet());
		assertThat(Set.of(1_000, 10_000, 100_000).containsAll(selectedSizes)).isTrue();
		assertThat(Set.of(10_000L, 100_000L, 20_100_000L).containsAll(selectedDistances)).isTrue();
		return approvedSizes.stream().filter(selectedSizes::contains)
				.flatMap(size -> approvedDistances.stream().filter(selectedDistances::contains)
						.flatMap(distance -> Stream.of("DENSE", "GLOBAL").flatMap(distribution -> Stream.of(false, true)
								.map(mixed -> new Workload(size, distance, distribution, mixed)))));
	}

	@ParameterizedTest
	@MethodSource("perf001")
	@DisplayName("PERF-001: 10,000명 current·혼합 키에서 repository·previewAll·신규 worker를 20회 예열하고 200회 측정한다")
	void measuresBaselineWorkload(Workload workload) throws Exception {
		run(workload, 1, "PERF-001");
	}

	@ParameterizedTest
	@MethodSource("perf002")
	@DisplayName("PERF-002: 1,000·100,000명과 세 거리에서 고정 seed의 분포·키별 한계를 측정한다")
	void measuresScaleAndDistanceLimits(Workload workload) throws Exception {
		run(workload, 1, "PERF-002");
	}

	@ParameterizedTest
	@MethodSource("perf003")
	@DisplayName("PERF-003: barrier로 시작한 매칭 4개와 preview가 commit까지 동일 후보·집계·중복 없는 수신자를 보존한다")
	void measuresFourWorkersWithPreview(Workload workload) throws Exception {
		run(workload, 4, "PERF-003");
	}

	private void run(Workload workload, int concurrency, String scenario) throws Exception {
		JsonNode environment = environment();
		String condition = workload + "-C" + concurrency;
		String status = "FAIL";
		try {
			if (Boolean.getBoolean("qello.performance.verify-only")) {
				loadMeasurements(scenario, condition, environment);
			} else {
				assertThat(Files.exists(rawPath(scenario, condition))).as("raw samples must not be overwritten")
						.isFalse();
				seed(workload);
				for (int round = -WARMUP - 1; round < MEASUREMENTS; round++) {
					String phase = round == -WARMUP - 1 ? "COLD" : round < 0 ? "WARMUP" : "MEASURED";
					iteration++;
					cleanupPosts();
					oracle.snapshot();
					List<DirectionCandidate> expected = oracle.candidates(parameters(workload, "N"));
					Map<String, Long> expectedCounts = counts(oracle.counts(parameters(workload, "N")));
					assertCandidates(measure("repository", phase, () -> candidates(workload, "N")), expected);
					List<Long> postIds = new ArrayList<>();
					for (int index = 0; index < concurrency; index++) {
						postIds.add(freshPost(workload, index));
					}
					if (concurrency == 1) {
						assertPreview(measure("preview", phase, () -> preview(workload)), expectedCounts);
						assertBatch(measure("worker", phase, () -> process("single-" + iteration)));
						assertCommitted(postIds.getFirst(), expected);
					} else {
						concurrentRound(workload, phase, expectedCounts, expected, postIds);
					}
				}
				recordPlans(workload, condition);
				summarize(condition);
				workloadCompleted = true;
			}
			assertSampleCounts(concurrency);
			requireExternalMetrics(condition, environment);
			if (!mainBaseline()) {
				compareExternalBaseline(condition, environment, workload, concurrency);
			}
			status = blocked.isEmpty() ? "PASS" : "BLOCKED";
		} finally {
			writeEvidence(scenario, condition, environment, status);
		}
		assertThat(blocked).as("BLOCKED: D6 필수 성능 증거").isEmpty();
	}

	private void concurrentRound(Workload workload, String phase, Map<String, Long> expectedCounts,
			List<DirectionCandidate> expected, List<Long> postIds) throws Exception {
		List<Long> scanned = expected.stream().limit(selection.maxRecipientsPerPost() * 3L)
				.map(DirectionCandidate::userId).toList();
		assertThat(scanned).as("동일 섹터의 실제 공유 잠금 후보").isNotEmpty();
		// ENSURE의 신규 행 경쟁이 SKIP LOCKED 측정을 가리지 않도록 측정 밖에서 초기화한다.
		receiveStates.ensureForUsers(scanned, NOW);
		LockRound locks = new LockRound();
		receiveStates.round = locks;
		AtomicLong started = new AtomicLong();
		AtomicLong completed = new AtomicLong();
		CyclicBarrier start = new CyclicBarrier(5, () -> started.set(System.nanoTime()));
		try (var executor = Executors.newFixedThreadPool(5)) {
			var workers = submitConcurrentWorkers(executor, start, phase, completed);
			Future<Timed<DirectionPreviewResult>> preview = executor.submit(() -> {
				start.await(60, TimeUnit.SECONDS);
				var result = timed("preview", phase, () -> preview(workload));
				completed.accumulateAndGet(System.nanoTime(), Math::max);
				return result;
			});
			try {
				for (var pending : workers) {
					Timed<DirectionMatchingWorker.BatchResult> result = pending.get(120, TimeUnit.SECONDS);
					retain(result.sample());
					assertBatch(result.result());
				}
				Timed<DirectionPreviewResult> result = preview.get(120, TimeUnit.SECONDS);
				retain(result.sample());
				assertPreview(result.result(), expectedCounts);
			} finally {
				workers.forEach(pending -> pending.cancel(true));
				preview.cancel(true);
				executor.shutdownNow();
			}
		} finally {
			receiveStates.round = null;
		}
		assertConcurrentCommitted(locks, scanned, expected, postIds);
		recordConcurrentSummary(phase, completed.get() - started.get(), locks);
	}

	private List<Future<Timed<DirectionMatchingWorker.BatchResult>>> submitConcurrentWorkers(
			ExecutorService executor, CyclicBarrier start, String phase, AtomicLong completed) {
		List<Future<Timed<DirectionMatchingWorker.BatchResult>>> workers = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			int index = i;
			workers.add(executor.submit(() -> {
				start.await(60, TimeUnit.SECONDS);
				String owner = "parallel-" + iteration + "-" + index;
				receiveStates.owner.set(owner);
				Timed<DirectionMatchingWorker.BatchResult> result;
				try {
					result = timed("worker", phase, () -> process(owner));
				} finally {
					receiveStates.owner.remove();
				}
				completed.accumulateAndGet(System.nanoTime(), Math::max);
				return result;
			}));
		}
		return workers;
	}

	private void recordConcurrentSummary(String phase, long elapsed, LockRound locks) {
		if (!"WARMUP".equals(phase)) {
			Map<String, Object> round = new LinkedHashMap<>();
			round.put("operation", "mixed-round");
			round.put("phase", phase);
			round.put("nanos", elapsed);
			round.put("committedWorkerCalls", 4);
			round.put("workerCallsPerSecond", 4_000_000_000.0 / (elapsed));
			round.put("lockedRows", locks.observations.values().stream()
					.mapToInt(observation -> observation.locked().size()).sum());
			round.put("synchronizationNanos", locks.observations.values().stream()
					.mapToLong(LockObservation::synchronizationNanos).boxed().toList());
			summaries.add(round);
		}
	}

	private void assertConcurrentCommitted(LockRound locks, List<Long> scanned,
			List<DirectionCandidate> expected, List<Long> postIds) {
		assertThat(locks.observations.keySet()).containsExactlyInAnyOrderElementsOf(postIds);
		List<Long> acquired = locks.observations.values().stream()
				.flatMap(observation -> observation.locked().stream()).toList();
		assertThat(acquired).as("commit 전 동시에 보유한 잠금은 독립 후보 창의 정확한 분할")
				.doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(scanned);
		List<Long> expectedRecipients = new ArrayList<>();
		for (long postId : postIds) {
			LockObservation observation = locks.observations.get(postId);
			assertThat(observation.scanned()).as("독립 oracle의 동일 섹터 스캔 순서")
					.containsExactlyElementsOf(scanned);
			List<DirectionCandidate> available = expected.stream()
					.filter(candidate -> observation.locked().contains(candidate.userId())).toList();
			assertThat(observation.locked()).as("실제 획득 잠금의 공정성 순서")
					.containsExactlyElementsOf(available.stream().map(DirectionCandidate::userId).toList());
			assertCommitted(postId, available);
			expectedRecipients.addAll(available.stream().limit(selection.maxRecipientsPerPost())
					.map(DirectionCandidate::userId).toList());
		}
		assertThat(expectedRecipients).doesNotHaveDuplicates();
		List<Long> reserved = jdbc.queryForList("""
				SELECT user_id FROM recipient_receive_state
				WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
					AND active_unhandled_count = 1 AND recent_received_count = 1
				""", Long.class, REGION);
		assertThat(reserved).as("수신자와 실제 예약 슬롯의 정확한 일치")
				.containsExactlyInAnyOrderElementsOf(expectedRecipients);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM recipient_receive_state
				WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
					AND (active_unhandled_count <> recent_received_count
						OR active_unhandled_count NOT IN (0, 1))
				""", Long.class, REGION)).as("중복 예약·카운터 불일치").isZero();
	}

	private DirectionMatchingWorker.BatchResult process(String owner) {
		return worker.processBatch(new DirectionMatchingWorker.BatchCommand(1, owner, NOW, NOW.plusSeconds(60),
				new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1))));
	}

	private long freshPost(Workload workload, int index) {
		long postId = posts.send(new DirectionPostService.SendCommand(sender, question, scheme, "N", 0,
				workload.distance(), REGION, "coordinate-perf-" + iteration + "-" + index,
				"합성 성능 질문", List.of(), NOW, NOW.plusSeconds(3600))).post().getId();
		jdbc.update("UPDATE direction_post SET moderation_status = 'PASSED' WHERE id = ?", postId);
		storage().prepareAudience(postId, ORIGIN, workload.mixedKeys() && iteration % 2 == 0);
		return postId;
	}

	private List<DirectionCandidate> candidates(Workload workload, String sector) {
		double center = SEGMENTS.indexOf(sector) * 45.0;
		return presenceRepository.findCandidates(sender, ORIGIN.latitude().doubleValue(),
				ORIGIN.longitude().doubleValue(), 0, workload.distance(),
				(center + 337.5) % 360, (center + 22.5) % 360, NOW, null);
	}

	private DirectionPreviewResult preview(Workload workload) {
		return posts.previewAll(new DirectionPostService.PreviewAllCommand(sender, scheme, 0,
				workload.distance(), null, NOW));
	}

	private MapSqlParameterSource parameters(Workload workload, String sector) {
		double center = SEGMENTS.indexOf(sector) * 45.0;
		return new MapSqlParameterSource("schemeId", scheme).addValue("excludedUserId", sender)
				.addValue("originLatitude", ORIGIN.latitude()).addValue("originLongitude", ORIGIN.longitude())
				.addValue("minDistanceMeters", 0L).addValue("maxDistanceMeters", workload.distance())
				.addValue("sectorStartDegrees", (center + 337.5) % 360)
				.addValue("sectorEndDegrees", (center + 22.5) % 360)
				.addValue("at", Timestamp.from(NOW)).addValue("regionCode", null);
	}

	private void seed(Workload workload) {
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level)"
				+ " VALUES (?, 'KR', 'Coordinate Performance', 'REGION')", REGION);
		jdbc.update("""
				INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
				SELECT 'USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'UTC', 'perf388-' || lpad(gs::text, 6, '0')
				FROM generate_series(1, ?) gs
				""", REGION, workload.size() + 1);
		List<Long> ids = jdbc.queryForList("SELECT id FROM user_account WHERE coarse_region_code = ? ORDER BY id",
				Long.class, REGION);
		sender = ids.getLast();
		scheme = jdbc.queryForObject("SELECT id FROM direction_scheme WHERE code = 'OCTANT' AND status = 'ACTIVE'",
				Long.class);
		question = jdbc.queryForObject(
				"""
						INSERT INTO approved_question
							(source_type, status, question_text, answer_format, active_from, active_until, approved_at, approved_by)
						VALUES ('OPERATOR', 'ACTIVE', 'coordinate performance', 'TEXT', ?, ?, ?, ?) RETURNING id
						""",
				Long.class, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW.plusSeconds(7200)),
				Timestamp.from(NOW.minusSeconds(60)), sender);
		seedCandidatePresence(workload, ids);
	}

	private void seedCandidatePresence(Workload workload, List<Long> ids) {
		oracle.reset();
		Random random = new Random(SEED);
		List<Presence> storageRows = new ArrayList<>(workload.size() + 1);
		List<Object[]> oracleRows = new ArrayList<>(workload.size() + 1);
		for (int i = 0; i < ids.size(); i++) {
			long id = ids.get(i);
			Location coordinate;
			if (id == sender) {
				coordinate = ORIGIN;
			} else if ("DENSE".equals(workload.distribution())) {
				coordinate = new Location(BigDecimal.valueOf(37.5 + (random.nextDouble() - 0.5) * 2),
						BigDecimal.valueOf(127 + (random.nextDouble() - 0.5) * 2));
			} else {
				coordinate = new Location(BigDecimal.valueOf(Math.toDegrees(Math.asin(2 * random.nextDouble() - 1))),
						BigDecimal.valueOf(random.nextDouble() * 360 - 180));
			}
			boolean receive = id != sender;
			storageRows.add(new Presence(id, coordinate, REGION, receive, NOW.minusSeconds(10),
					NOW.plusSeconds(7200), workload.mixedKeys() && i % 2 == 0));
			oracleRows.add(new Object[]{id, coordinate.longitude(), coordinate.latitude(), REGION, receive,
					Timestamp.from(NOW.minusSeconds(10)), Timestamp.from(NOW.plusSeconds(7200))});
		}
		storage().seedPresence(storageRows);
		assertThat(storageRows.stream().filter(Presence::receiveAllowed).count()).isEqualTo((long) workload.size());
		jdbc.batchUpdate("""
				INSERT INTO coordinate_oracle.active_user_presence
					(user_id, position, coarse_region_code, receive_allowed, location_at, expires_at)
				VALUES (?, ST_SetSRID(ST_MakePoint(?, ?),4326)::geography, ?, ?, ?, ?)
				""", oracleRows);
		jdbc.execute("ANALYZE active_user_presence, user_account");
	}

	private void cleanupPosts() {
		namedJdbc.update("""
				DELETE FROM outbox_event WHERE aggregate_type = 'POST_RECIPIENT'
				AND aggregate_id IN (SELECT id FROM post_recipient WHERE post_id IN
					(SELECT id FROM direction_post WHERE coarse_region_code = :region))
				""", new MapSqlParameterSource("region", REGION));
		jdbc.update("DELETE FROM post_recipient WHERE post_id IN"
				+ " (SELECT id FROM direction_post WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM outbox_event WHERE aggregate_type = 'DIRECTION_POST' AND aggregate_id IN"
				+ " (SELECT id FROM direction_post WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM post_audience WHERE post_id IN"
				+ " (SELECT id FROM direction_post WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM direction_post WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM recipient_receive_state WHERE user_id IN"
				+ " (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
	}

	private void assertBatch(DirectionMatchingWorker.BatchResult result) {
		assertThat(result.claimed()).as("새 매칭 이벤트 claim").isEqualTo(1);
		assertThat(result.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
	}

	private void assertCandidates(List<DirectionCandidate> actual, List<DirectionCandidate> expected) {
		assertThat(actual.size()).as("후보 누락·추가").isEqualTo(expected.size());
		for (int i = 0; i < expected.size(); i++) {
			var left = actual.get(i);
			var right = expected.get(i);
			assertThat(left.userId().equals(right.userId())).as("후보 순서·집합 일치").isTrue();
			assertThat(left.distanceMeters().subtract(right.distanceMeters()).abs().doubleValue())
					.as("거리 오차 m").isLessThanOrEqualTo(0.001);
			assertThat(angleDifference(left.bearingDegrees(), right.bearingDegrees()))
					.as("방위 순환 오차 deg").isLessThanOrEqualTo(0.0000001);
			assertThat(angleDifference(left.inboundBearingDegrees(), right.inboundBearingDegrees()))
					.as("역방위 순환 오차 deg").isLessThanOrEqualTo(0.0000001);
		}
	}

	private static double angleDifference(BigDecimal left, BigDecimal right) {
		double difference = Math.abs(left.doubleValue() - right.doubleValue()) % 360;
		return Math.min(difference, 360 - difference);
	}

	private static Map<String, Long> counts(List<DirectionSegmentCandidateCount> counts) {
		return counts.stream().collect(Collectors.toMap(DirectionSegmentCandidateCount::segmentKey,
				DirectionSegmentCandidateCount::count));
	}

	private void assertPreview(DirectionPreviewResult actual, Map<String, Long> expected) {
		assertThat(actual.segments().stream().map(DirectionPreviewResult.SegmentCount::segmentKey).toList())
				.containsExactlyElementsOf(SEGMENTS);
		assertThat(actual.segments().stream().collect(Collectors.toMap(
				DirectionPreviewResult.SegmentCount::segmentKey, DirectionPreviewResult.SegmentCount::count)))
				.isEqualTo(expected);
	}

	private void assertCommitted(long postId, List<DirectionCandidate> expected) {
		List<Long> recipients = jdbc.queryForList(
				"SELECT recipient_id FROM post_recipient WHERE post_id = ? ORDER BY id", Long.class, postId);
		List<Long> expectedIds = expected.stream().limit(selection.maxRecipientsPerPost())
				.map(DirectionCandidate::userId).toList();
		assertThat(recipients.equals(expectedIds)).as("commit된 수신자 순서·누락·중복").isTrue();
		assertThat(jdbc.queryForObject("SELECT status FROM direction_post WHERE id = ?", String.class, postId))
				.isEqualTo("ACTIVE");
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM outbox_event WHERE aggregate_type = 'DIRECTION_POST'
					AND aggregate_id = ? AND event_type = 'RECIPIENT_MATCH_REQUESTED' AND status = 'PROCESSED'
				""", Long.class, postId)).as("매칭 완료 commit").isEqualTo(1L);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM outbox_event WHERE aggregate_type = 'POST_RECIPIENT'
					AND aggregate_id IN (SELECT id FROM post_recipient WHERE post_id = ?)
					AND event_type = 'RECIPIENTS_CONFIRMED'
				""", Long.class, postId)).as("수신자별 확정 이벤트").isEqualTo((long) expectedIds.size());
	}

	private <T> T measure(String operation, String phase, Supplier<T> action) {
		Timed<T> result = timed(operation, phase, action);
		retain(result.sample());
		return result.result();
	}

	private static <T> Timed<T> timed(String operation, String phase, Supplier<T> action) {
		RuntimeObservation before = runtime();
		long start = System.nanoTime();
		T result = action.get();
		long nanos = System.nanoTime() - start;
		RuntimeObservation after = runtime();
		return new Timed<>(result, new Sample(operation, phase, nanos,
				delta(before.cpuNanos(), after.cpuNanos()), before.heapBytes(), after.heapBytes(),
				delta(before.gcCount(), after.gcCount()), delta(before.gcMillis(), after.gcMillis())));
	}

	private void retain(Sample sample) {
		if (!"WARMUP".equals(sample.phase())) {
			samples.add(sample);
		}
	}

	private static RuntimeObservation runtime() {
		var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		long gcCount = 0;
		long gcMillis = 0;
		for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
			if (gc.getCollectionCount() < 0 || gc.getCollectionTime() < 0) {
				return new RuntimeObservation(known(os.getProcessCpuTime()),
						ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(), null, null);
			}
			gcCount += gc.getCollectionCount();
			gcMillis += gc.getCollectionTime();
		}
		return new RuntimeObservation(known(os.getProcessCpuTime()),
				ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(), gcCount, gcMillis);
	}

	private static Long known(long value) {
		return value < 0 ? null : value;
	}

	private static Long delta(Long before, Long after) {
		return before == null || after == null ? null : after - before;
	}

	private void summarize(String condition) {
		for (String operation : List.of("repository", "preview", "worker")) {
			List<Long> nanos = samples.stream().filter(sample -> sample.operation().equals(operation)
					&& sample.phase().equals("MEASURED")).map(Sample::nanos).sorted().toList();
			Map<String, Object> summary = new LinkedHashMap<>();
			summary.put("condition", condition);
			summary.put("operation", operation);
			summary.put("samples", nanos.size());
			summary.put("p50Nanos", percentile(nanos, 0.50));
			summary.put("p95Nanos", percentile(nanos, 0.95));
			summary.put("p99Nanos", percentile(nanos, 0.99));
			summary.put("serviceCallsPerSecond", nanos.size() * 1_000_000_000.0
					/ nanos.stream().mapToLong(Long::longValue).sum());
			summaries.add(summary);
		}
	}

	private static long percentile(List<Long> sorted, double percentile) {
		return sorted.get((int) Math.ceil(sorted.size() * percentile) - 1);
	}

	private JsonNode environment() throws IOException {
		String path = System.getProperty("qello.performance.environment-file");
		assertThat(path).as("BLOCKED: D6 환경 manifest 필요").isNotBlank();
		JsonNode input = mapper.readTree(Path.of(path).toFile());
		var environment = mapper.createObjectNode();
		for (String field : ENVIRONMENT_FIELDS) {
			assertThat(input.hasNonNull(field)).as("BLOCKED: 환경 필드 %s", field).isTrue();
			environment.set(field, input.get(field));
		}
		assertThat(System.getProperty("os.arch")).as("D6 JVM arch").isIn("aarch64", "arm64");
		assertThat(environment.path("cpuModel").asText()).contains("Neoverse-N1");
		assertThat(environment.path("arch").asText()).isIn("aarch64", "arm64");
		assertThat(environment.path("hostCores").asInt()).isEqualTo(2);
		assertThat(environment.path("dbCpuAllocation").asInt()).isEqualTo(1);
		assertThat(environment.path("jvmCpuAllocation").asInt()).isEqualTo(1);
		assertThat(environment.path("imageDigest").asText()).matches("sha256:[0-9a-f]{64}");
		assertThat(postgres.getDockerImageName()).contains("imresamu/postgis");
		assertThat(postgres.getDockerImageName()).endsWith("@" + environment.path("imageDigest").asText());
		assertThat(environment.path("jdk").asText()).isEqualTo(System.getProperty("java.runtime.version"));
		assertThat(environment.path("postgres").asText())
				.isEqualTo(jdbc.queryForObject("SHOW server_version", String.class));
		assertThat(environment.path("postgis").asText())
				.isEqualTo(jdbc.queryForObject("SELECT postgis_full_version()", String.class));
		assertThat(environment.path("jvmHeapBytes").asLong()).isEqualTo(Runtime.getRuntime().maxMemory());
		if (mainBaseline()) {
			assertThat(environment.path("geographicLib").asText()).isEqualTo("NOT_APPLICABLE");
		} else {
			assertThat(environment.path("geographicLib").asText()).isNotBlank().isNotEqualTo("NOT_APPLICABLE");
		}
		assertThat(environment.path("dbMemoryLimitBytes").asLong())
				.isEqualTo(Long.parseLong(System.getProperty("qello.performance.db-memory-bytes")));
		return environment;
	}

	private void compareExternalBaseline(String condition, JsonNode environment, Workload workload, int concurrency)
			throws IOException {
		String path = System.getProperty("qello.performance.baseline-file");
		if (path == null || !Files.exists(Path.of(path))) {
			blocked.add("main full-flow baseline missing; SQL oracle is not a performance baseline");
			return;
		}
		JsonNode baseline = mapper.readTree(Path.of(path).toFile());
		assertThat(baseline.path("variant").asText()).isEqualTo("MAIN_PLAINTEXT_FULL_FLOW");
		assertThat(baseline.path("commit").asText()).isEqualTo(requiredCommit("main-commit"));
		assertThat(baseline.path("commit").asText()).isNotEqualTo(requiredCommit("candidate-commit"));
		assertThat(baseline.path("warmup").asInt()).isEqualTo(WARMUP);
		assertThat(baseline.path("measurements").asInt()).isEqualTo(MEASUREMENTS);
		assertThat(baseline.path("seed").asLong()).isEqualTo(SEED);
		assertBaselineEnvironment(baseline.path("environment"), environment);
		assertThat(baseline.path("boundaries")).isEqualTo(mapper.valueToTree(BOUNDARIES));
		if (concurrency == 4) {
			assertThat(baseline.path("lockProtocol").asText()).as("동일한 동시 잠금 계측 경계")
					.isEqualTo(LOCK_PROTOCOL);
		}
		JsonNode reference = baseline.path("conditions").path(condition);
		if (reference.isMissingNode()) {
			blocked.add("main baseline condition missing: " + condition);
			return;
		}
		byte[] raw = verifiedRaw(reference);
		JsonNode rawResult = mapper.readTree(raw);
		assertThat(rawResult.path("workloadCompleted").asBoolean()).as("complete main workload").isTrue();
		assertThat(rawResult.path("status").asText()).isIn("PASS", "BLOCKED");
		if ("BLOCKED".equals(rawResult.path("status").asText())) {
			blocked.add("main baseline mandatory metrics unresolved: " + condition);
		}
		assertThat(rawResult.path("variant").asText()).isEqualTo("MAIN_PLAINTEXT_FULL_FLOW");
		assertThat(rawResult.path("commit")).isEqualTo(baseline.path("commit"));
		assertBaselineEnvironment(rawResult.path("environment"), environment);
		assertThat(rawResult.path("boundaries")).isEqualTo(mapper.valueToTree(BOUNDARIES));
		assertThat(rawResult.path("condition").asText()).isEqualTo(condition);
		assertThat(rawResult.path("warmup").asInt()).isEqualTo(WARMUP);
		assertThat(rawResult.path("measurements").asInt()).isEqualTo(MEASUREMENTS);
		assertThat(rawResult.path("seed").asLong()).isEqualTo(SEED);
		if (concurrency == 4) {
			assertThat(rawResult.path("lockProtocol").asText()).isEqualTo(LOCK_PROTOCOL);
		}
		compareBaselineSamples(rawResult, reference, workload, concurrency);
	}

	private void compareBaselineSamples(JsonNode rawResult, JsonNode reference, Workload workload, int concurrency) {
		for (var summary : summaries) {
			String operation = (String) summary.get("operation");
			if (!BOUNDARIES.containsKey(operation)) {
				continue;
			}
			List<Long> measured = new ArrayList<>();
			for (JsonNode sample : rawResult.path("samples")) {
				if (sample.path("operation").asText().equals(operation)
						&& sample.path("phase").asText().equals("MEASURED")) {
					long nanos = sample.path("nanos").asLong(-1);
					assertThat(nanos).as("main 원자료 지연").isPositive();
					measured.add(nanos);
				}
			}
			assertThat(measured).as("외부 main 원자료 표본 수").hasSize(
					"worker".equals(operation) ? MEASUREMENTS * concurrency : MEASUREMENTS);
			measured.sort(Long::compareTo);
			long referenceP95 = percentile(measured, 0.95);
			double ratio = ((Number) summary.get("p95Nanos")).doubleValue() / referenceP95;
			summary.put("mainP95Ratio", ratio);
			summary.put("baselineRawSha256", reference.path("rawSha256").asText());
			if (workload.size() == 10_000 && concurrency == 1 && !"repository".equals(operation)) {
				assertThat(ratio).as("D6 %s main 전체 흐름 대비 p95", operation).isLessThanOrEqualTo(2.0);
			}
		}
	}

	private void requireExternalMetrics(String condition, JsonNode environment) throws IOException {
		String path = System.getProperty("qello.performance.metrics-file");
		if (path == null || !Files.exists(Path.of(path))) {
			EXTERNAL_METRICS.forEach(field -> blocked.add(field + "=UNKNOWN/BLOCKED"));
			return;
		}
		JsonNode evidence = mapper.readTree(Path.of(path).toFile());
		assertThat(evidence.path("environment")).isEqualTo(environment);
		assertThat(evidence.path("commit").asText()).isEqualTo(activeCommit());
		JsonNode rawMetrics = mapper.readTree(verifiedRaw(evidence));
		assertThat(rawMetrics.path("environment")).isEqualTo(environment);
		assertThat(rawMetrics.path("commit")).isEqualTo(evidence.path("commit"));
		assertThat(rawMetrics.path("conditions").path(condition))
				.isEqualTo(evidence.path("conditions").path(condition));
		Map<String, Object> metrics = new LinkedHashMap<>();
		metrics.put("operation", "external-metrics");
		metrics.put("rawSha256", evidence.path("rawSha256").asText());
		for (String metric : EXTERNAL_METRICS) {
			JsonNode value = evidence.path("conditions").path(condition).path(metric);
			if (!value.isNumber()) {
				blocked.add(metric + "=UNKNOWN/BLOCKED");
			} else if ("leaseHeadroomNanos".equals(metric)) {
				assertThat(value.asLong()).as("실측 lease 여유").isBetween(1L, LEASE_NANOS);
			} else {
				assertThat(value.asDouble()).as("외부 실측 %s", metric).isGreaterThanOrEqualTo(0);
			}
			metrics.put(metric, value.isNumber() ? value.numberValue() : "UNKNOWN/BLOCKED");
		}
		summaries.add(metrics);
		if (samples.stream().anyMatch(sample -> sample.appCpuNanos() == null
				|| sample.gcCount() == null || sample.gcMillis() == null)) {
			blocked.add("app CPU or GC counters unsupported=UNKNOWN/BLOCKED");
		}
	}

	private static String requiredCommit(String name) {
		String commit = System.getProperty("qello.performance." + name);
		assertThat(commit).as("BLOCKED: %s 고정 commit 필요", name).matches("[0-9a-f]{40}");
		return commit;
	}

	private static byte[] verifiedRaw(JsonNode manifest) throws IOException {
		assertThat(manifest.path("rawFile").asText()).as("BLOCKED: 외부 실측 원자료 필요").isNotBlank();
		byte[] raw = Files.readAllBytes(Path.of(manifest.path("rawFile").asText()));
		try {
			String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
			assertThat(manifest.path("rawSha256").asText()).as("외부 원자료 무결성").isEqualTo(hash);
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
		return raw;
	}

	private void recordPlans(Workload workload, String condition) {
		var probe = new DirectionMatchingPerformanceProbe(jdbc, namedJdbc, mapper);
		for (String operation : List.of("repository", "preview")) {
			var plan = probe.explain(operation, condition, "repository".equals(operation)
					? ActiveUserPresenceSql.FIND_CANDIDATES_SQL
					: ActiveUserPresenceSql.FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL, parameters(workload, "N"));
			assertThat(plan.targetNodes()).isNotEmpty();
			Map<String, Object> summary = new LinkedHashMap<>();
			summary.put("condition", condition);
			summary.put("operation", operation + "-plan");
			summary.put("plan", plan);
			// SQL 계획은 새 앱 계산의 일부일 뿐 main 전체 흐름 비율의 분모가 아니다.
			summaries.add(summary);
		}
	}

	private void writeEvidence(String scenario, String condition, JsonNode environment, String status)
			throws IOException, NoSuchAlgorithmException {
		Path directory = evidenceDirectory();
		Files.createDirectories(directory);
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("scenario", scenario);
		evidence.put("variant", storage().variant());
		evidence.put("commit", activeCommit());
		evidence.put("condition", condition);
		evidence.put("status", status);
		evidence.put("workloadCompleted", workloadCompleted);
		if (verificationOfRawSha256 != null) {
			evidence.put("verificationOfRawSha256", verificationOfRawSha256);
		}
		evidence.put("seed", SEED);
		evidence.put("warmup", WARMUP);
		evidence.put("measurements", MEASUREMENTS);
		evidence.put("environment", environment);
		evidence.put("boundaries", BOUNDARIES);
		evidence.put("lockProtocol", LOCK_PROTOCOL);
		evidence.put("oracleBaselineCommit", FrozenPostgisOracle.BASELINE_COMMIT);
		evidence.put("oracleBaselineSourceSha256", FrozenPostgisOracle.BASELINE_SOURCE_SHA256);
		evidence.put("summaries", summaries);
		evidence.put("samples", samples);
		evidence.put("blocked", blocked);
		evidence.put("cpuCounterScope", "process-wide; concurrent per-call deltas overlap, do not sum");
		evidence.put("throughputScope", "inverse mean service time, not concurrent wall throughput");
		byte[] raw = mapper.writeValueAsBytes(evidence);
		String verificationId = System.getProperty("qello.performance.verification-id", "verified");
		assertThat(verificationId).as("verification evidence file suffix").matches("[A-Za-z0-9_-]+");
		Path result = Boolean.getBoolean("qello.performance.verify-only")
				? directory.resolve(scenario + "-" + condition + "." + verificationId + ".json")
				: rawPath(scenario, condition);
		Files.write(result, raw, java.nio.file.StandardOpenOption.CREATE_NEW);
		if (mainBaseline()) {
			writeBaselineManifest(evidence, result, raw);
		}
		System.out.println(scenario + " condition=" + condition + " status=" + status + " samples=" + samples.size()
				+ " raw_sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
	}

	record Workload(int size, long distance, String distribution, boolean mixedKeys) {
		@Override
		public String toString() {
			return size + "-" + distance + "-" + distribution + "-" + (mixedKeys ? "MIXED" : "CURRENT");
		}
	}

	private record RuntimeObservation(Long cpuNanos, long heapBytes, Long gcCount, Long gcMillis) {
	}

	private record Sample(String operation, String phase, long nanos, Long appCpuNanos,
			long heapBeforeBytes, long heapAfterBytes, Long gcCount, Long gcMillis) {
	}

	private record Timed<T>(T result, Sample sample) {
	}

	@TestConfiguration
	static class LockObservationConfiguration {
		@Bean
		@Primary
		ObservedReceiveStates observedReceiveStates(
				@Qualifier("jdbcRecipientReceiveStateRepository") RecipientReceiveStateRepository delegate,
				JdbcTemplate jdbc) {
			return new ObservedReceiveStates(delegate, jdbc);
		}
	}

	private static final class LockRound {
		private final CyclicBarrier beforeLock = new CyclicBarrier(4);
		private final CyclicBarrier afterLock = new CyclicBarrier(4);
		private final Map<Long, LockObservation> observations = new ConcurrentHashMap<>();
	}

	private record LockObservation(List<Long> scanned, List<Long> locked, long synchronizationNanos) {
	}

	static final class ObservedReceiveStates implements RecipientReceiveStateRepository {
		private final RecipientReceiveStateRepository delegate;
		private final JdbcTemplate jdbc;
		private final ThreadLocal<String> owner = new ThreadLocal<>();
		private volatile LockRound round;

		ObservedReceiveStates(RecipientReceiveStateRepository delegate, JdbcTemplate jdbc) {
			this.delegate = delegate;
			this.jdbc = jdbc;
		}

		@Override
		public List<Long> lockAvailableUserIds(List<LockCandidate> candidates, int limit, int activeLimit) {
			LockRound active = round;
			if (active == null) {
				return delegate.lockAvailableUserIds(candidates, limit, activeLimit);
			}
			long postId = jdbc.queryForObject("""
					SELECT aggregate_id FROM outbox_event WHERE lease_owner = ?
						AND aggregate_type = 'DIRECTION_POST' AND event_type = 'RECIPIENT_MATCH_REQUESTED'
						AND status = 'PROCESSING'
					""", Long.class, owner.get());
			long synchronization = await(active.beforeLock);
			List<Long> locked = delegate.lockAvailableUserIds(candidates, limit, activeLimit);
			// 모든 실제 SELECT가 끝나기 전에는 어느 worker도 예약·commit으로 잠금을 풀지 않는다.
			synchronization += await(active.afterLock);
			assertThat(active.observations.put(postId, new LockObservation(
					candidates.stream().map(LockCandidate::userId).toList(), List.copyOf(locked), synchronization)))
					.isNull();
			return locked;
		}

		private static long await(CyclicBarrier barrier) {
			long started = System.nanoTime();
			try {
				barrier.await(60, TimeUnit.SECONDS);
				return System.nanoTime() - started;
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("performance lock barrier interrupted", interrupted);
			} catch (BrokenBarrierException | TimeoutException failure) {
				throw new IllegalStateException("performance lock barrier failed", failure);
			}
		}

		@Override
		public RecipientReceiveState save(RecipientReceiveState state) {
			return delegate.save(state);
		}

		@Override
		public Optional<RecipientReceiveState> findByUserId(long userId) {
			return delegate.findByUserId(userId);
		}

		@Override
		public boolean reserve(long userId, Instant receivedAt, int activeLimit) {
			return delegate.reserve(userId, receivedAt, activeLimit);
		}

		@Override
		public boolean release(long userId, Instant releasedAt) {
			return delegate.release(userId, releasedAt);
		}

		@Override
		public void ensureForUsers(List<Long> userIds, Instant at) {
			delegate.ensureForUsers(userIds, at);
		}
	}
	private static final class FrozenPostgisOracle {
		static final String BASELINE_COMMIT = "a910241e140071b3b34e9e05f461efdc7e43e24d";
		static final String BASELINE_SOURCE_SHA256 = "9026c39ce7a7a393b713a5d7835a56b2ad21d746b576fd0319a161c668bf65ae";
		private static final List<String> SNAPSHOT_TABLES = List.of("user_account", "recipient_receive_state",
				"user_block", "direction_scheme", "direction_segment");
		private final JdbcTemplate jdbc;
		private final NamedParameterJdbcTemplate named;

		FrozenPostgisOracle(JdbcTemplate jdbc) {
			this.jdbc = jdbc;
			this.named = new NamedParameterJdbcTemplate(jdbc);
		}

		void reset() {
			jdbc.execute("CREATE SCHEMA IF NOT EXISTS coordinate_oracle");
			jdbc.execute("""
					CREATE TABLE IF NOT EXISTS coordinate_oracle.active_user_presence (
						user_id bigint PRIMARY KEY, position geography(Point, 4326), coarse_region_code varchar,
						receive_allowed boolean, location_at timestamptz, expires_at timestamptz)
					""");
			jdbc.execute("TRUNCATE coordinate_oracle.active_user_presence");
		}

		void snapshot() {
			for (String table : SNAPSHOT_TABLES) {
				jdbc.execute("CREATE TABLE IF NOT EXISTS coordinate_oracle." + table
						+ " AS SELECT * FROM public." + table + " WITH NO DATA");
				jdbc.execute("TRUNCATE coordinate_oracle." + table);
				jdbc.execute("INSERT INTO coordinate_oracle." + table + " SELECT * FROM public." + table);
			}
		}

		List<DirectionCandidate> candidates(MapSqlParameterSource parameters) {
			return named.query(isolated(FIND_CANDIDATES_SQL), parameters,
					(rs, row) -> new DirectionCandidate(rs.getLong("user_id"), rs.getBigDecimal("distance_m"),
							rs.getBigDecimal("bearing_deg"), rs.getString("coarse_region_code"),
							rs.getBigDecimal("inbound_bearing_deg")));
		}

		List<DirectionSegmentCandidateCount> counts(MapSqlParameterSource parameters) {
			return named.query(isolated(FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL), parameters,
					(rs, row) -> new DirectionSegmentCandidateCount(rs.getString("segment_key"),
							rs.getLong("candidate_count")));
		}

		private static String isolated(String sql) {
			String result = sql.replace("active_user_presence p", "coordinate_oracle.active_user_presence p");
			for (String table : SNAPSHOT_TABLES) {
				result = result.replace(table + " ", "coordinate_oracle." + table + " ");
			}
			return result;
		}

		public static final String FIND_CANDIDATES_SQL = """
				WITH origin AS (
					SELECT ST_SetSRID(ST_MakePoint(:originLongitude, :originLatitude), 4326)::geography AS point
				), candidates AS (
					SELECT p.user_id, p.coarse_region_code,
					       ST_Distance(p.position, origin.point) AS distance_m,
					       DEGREES(ST_Azimuth(origin.point, p.position)) AS bearing_deg,
					       DEGREES(ST_Azimuth(p.position, origin.point)) AS inbound_bearing_deg,
					       COALESCE(rrs.recent_received_count, 0) AS recent_received_count,
					       rrs.last_received_at
					FROM active_user_presence p
					JOIN user_account ua ON ua.id = p.user_id
					LEFT JOIN recipient_receive_state rrs ON rrs.user_id = p.user_id
					CROSS JOIN origin
					WHERE p.user_id <> :excludedUserId
					  AND ua.status = 'ACTIVE'
					  AND p.position IS NOT NULL
					  AND p.receive_allowed = TRUE
					  AND p.location_at <= :at
					  AND p.expires_at > :at
					  AND (CAST(:regionCode AS VARCHAR) IS NULL OR p.coarse_region_code = CAST(:regionCode AS VARCHAR))
					  AND ST_DWithin(p.position, origin.point, :maxDistanceMeters)
					  AND NOT EXISTS (SELECT 1 FROM user_block ub
					                  WHERE ub.blocker_id = :excludedUserId
					                    AND ub.blocked_id = p.user_id
					                    AND ub.released_at IS NULL)
					  AND NOT EXISTS (SELECT 1 FROM user_block ub
					                  WHERE ub.blocker_id = p.user_id
					                    AND ub.blocked_id = :excludedUserId
					                    AND ub.released_at IS NULL)
				)
				SELECT user_id, distance_m, bearing_deg, inbound_bearing_deg, coarse_region_code
				FROM candidates
				WHERE distance_m >= :minDistanceMeters
				  AND ((:sectorStartDegrees < :sectorEndDegrees AND bearing_deg >= :sectorStartDegrees AND bearing_deg < :sectorEndDegrees)
				    OR (:sectorStartDegrees >= :sectorEndDegrees AND (bearing_deg >= :sectorStartDegrees OR bearing_deg < :sectorEndDegrees)))
				ORDER BY recent_received_count, last_received_at NULLS FIRST, distance_m, user_id
				""";

		/**
		 * 활성 scheme의 모든 segment를 기준으로 후보를 한 번에 집계한다. segment를 기준으로 LEFT JOIN하기 때문에 후보가
		 * 없는 방향도 결과에서 사라지지 않는다.
		 */
		public static final String FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL = """
				WITH origin AS (
					SELECT ST_SetSRID(ST_MakePoint(:originLongitude, :originLatitude), 4326)::geography AS point
				), candidate_bearings AS (
					SELECT p.user_id,
					       ST_Distance(p.position, origin.point) AS distance_m,
					       DEGREES(ST_Azimuth(origin.point, p.position)) AS bearing_deg
					FROM active_user_presence p
					JOIN user_account ua ON ua.id = p.user_id
					CROSS JOIN origin
					WHERE p.user_id <> :excludedUserId
					  AND ua.status = 'ACTIVE'
					  AND p.position IS NOT NULL
					  AND p.receive_allowed = TRUE
					  AND p.location_at <= :at
					  AND p.expires_at > :at
					  AND (CAST(:regionCode AS VARCHAR) IS NULL OR p.coarse_region_code = CAST(:regionCode AS VARCHAR))
					  AND ST_DWithin(p.position, origin.point, :maxDistanceMeters)
					  AND NOT EXISTS (SELECT 1 FROM user_block ub
					                  WHERE ub.blocker_id = :excludedUserId
					                    AND ub.blocked_id = p.user_id
					                    AND ub.released_at IS NULL)
					  AND NOT EXISTS (SELECT 1 FROM user_block ub
					                  WHERE ub.blocker_id = p.user_id
					                    AND ub.blocked_id = :excludedUserId
					                    AND ub.released_at IS NULL)
				), eligible_candidates AS (
					SELECT user_id, distance_m, bearing_deg
					FROM candidate_bearings
					WHERE distance_m >= :minDistanceMeters
					  AND distance_m <= :maxDistanceMeters
				)
				SELECT ds.segment_key, COUNT(ec.user_id) AS candidate_count
				FROM direction_scheme scheme
				JOIN direction_segment ds ON ds.scheme_id = scheme.id
				CROSS JOIN LATERAL (
					SELECT MOD(ds.center_bearing_deg - ds.angular_width_deg / 2 + 360, 360) AS start_deg,
					       MOD(ds.center_bearing_deg + ds.angular_width_deg / 2 + 360, 360) AS end_deg
				) bounds
				LEFT JOIN eligible_candidates ec
					ON ((bounds.start_deg < bounds.end_deg
							AND ec.bearing_deg >= bounds.start_deg AND ec.bearing_deg < bounds.end_deg)
						OR (bounds.start_deg >= bounds.end_deg
							AND (ec.bearing_deg >= bounds.start_deg OR ec.bearing_deg < bounds.end_deg)))
				WHERE scheme.id = :schemeId
				  AND scheme.status = 'ACTIVE'
				GROUP BY ds.segment_key, ds.sort_order
				ORDER BY ds.sort_order
				""";
	}
}
