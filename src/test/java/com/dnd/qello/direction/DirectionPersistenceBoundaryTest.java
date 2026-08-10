package com.dnd.qello.direction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Created at: 2026-08-03T20:30:00+09:00
 * Source scenario: TEST-PLAN-GH-39-DIRECTION-POSTGIS-PERSISTENCE-UNIT-007
 * Source scenario: TEST-PLAN-GH-94-RECEIVE-STATE-INIT-RACE-UNIT-001 through UNIT-002 (2026-08-10T15:15:11+09:00)
 */
class DirectionPersistenceBoundaryTest {

	@Test
	@DisplayName("Direction domain과 repository port는 Spring/JPA 구현에 의존하지 않는다")
	void domainAndPortsRemainIndependent() throws IOException {
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello/direction/domain"))) {
			assertThat(paths.filter(path -> path.toString().endsWith(".java"))
				.map(this::read).allMatch(source -> !source.contains("jakarta.persistence") && !source.contains("org.springframework"))).isTrue();
		}
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello/direction/repository"))) {
			List<String> ports = paths.filter(path -> path.toString().endsWith(".java"))
				.filter(path -> !path.toString().contains("/jdbc/"))
				.filter(path -> !path.toString().contains("/jpa/"))
				.map(this::read).toList();
			assertThat(ports).allMatch(source -> !source.contains("jakarta.persistence") && !source.contains("org.springframework.data"));
		}
	}

	@Test
	@DisplayName("Direction 구현은 다른 feature의 JPA Entity와 Repository를 직접 참조하지 않는다")
	void otherFeaturesDoNotReferenceDirectionImplementation() throws IOException {
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello"))) {
			assertThat(paths.filter(path -> path.toString().endsWith(".java"))
				.filter(path -> !path.toString().toString().contains("/direction/"))
				.map(this::read).allMatch(source -> !source.contains("direction.repository.jdbc") && !source.contains("direction.repository.jpa"))).isTrue();
		}
	}

	/**
	 * UNIT-001. 조회해서 없으면 만들고 다시 예약하는 2단계 초기화는 원자적이지 않아
	 * 동시 발송이 서로의 예약을 덮어썼다(#94). 이 가드는 그 패턴이 되돌아오는 것을 막는다.
	 * 원자성 자체의 증거는 ReceiveStateReservationIntegrationTest의 INT-001·INT-002다.
	 */
	@Test
	@DisplayName("발송 경로는 수신 상태를 조회한 뒤 초기 행을 만드는 2단계 초기화를 쓰지 않는다")
	void sendPathDoesNotInitializeReceiveStateInTwoSteps() {
		String source = read(Path.of("src/main/java/com/dnd/qello/direction/service/DirectionPostService.java"));

		assertThat(source).doesNotContain("receiveStateRepository.findByUserId");
		assertThat(source).doesNotContain("receiveStateRepository.save");
	}

	/**
	 * UNIT-002. 예약·시딩·해제 세 SQL은 서로 다른 계약을 갖는다. 하나를 고치다 다른 하나의
	 * 계약을 함께 바꾸면 조용히 깨진다 — 특히 SAVE의 덮어쓰기가 사라지면 이 값을 시더로
	 * 쓰는 통합 테스트들이 무력화되고, RELEASE 변경은 슬롯 해제 경로(#93)를 깨뜨린다.
	 * SQL은 RecipientReceiveStateSql로 추출되어 있어(다른 리포지토리와 같은 관례)
	 * 상수 단위로 검사한다.
	 */
	@Test
	@DisplayName("수신 상태 예약은 단일 UPSERT이고 시딩과 해제의 기존 계약은 그대로다")
	void receiveStateSqlKeepsSeparateContractsPerOperation() {
		String source = read(Path.of("src/main/java/com/dnd/qello/direction/repository/jdbc/sql/RecipientReceiveStateSql.java"));
		String reserve = constantBody(source, "RESERVE");
		String save = constantBody(source, "SAVE");
		String release = constantBody(source, "RELEASE");

		// 예약은 INSERT와 UPDATE를 한 문장으로 합친다. 두 문으로 나뉘면 그 사이가 다시 경쟁 구간이 된다.
		assertThat(reserve).contains("ON CONFLICT (user_id) DO UPDATE");
		assertThat(reserve).contains("active_unhandled_count < :activeLimit");
		// 충돌 시 기존 값을 읽어 증가시킨다. EXCLUDED(제안값)를 쓰면 나중 트랜잭션이 먼저 예약된 슬롯을 지운다.
		assertThat(reserve).doesNotContain("EXCLUDED");

		// 시더는 반대로 덮어쓰는 것이 계약이다.
		assertThat(save).contains("EXCLUDED.active_unhandled_count");

		// 해제는 이번 변경의 범위 밖이다.
		assertThat(release).contains("active_unhandled_count = active_unhandled_count - 1");
		assertThat(release).contains("active_unhandled_count > 0");
	}

	/** {@code public static final String <name> = """} 선언부터 닫는 {@code """;}까지를 본문으로 본다. */
	private String constantBody(String source, String name) {
		String signature = "String " + name + " = \"\"\"";
		int start = source.indexOf(signature);
		assertThat(start).describedAs("상수를 찾지 못했습니다: %s", name).isNotNegative();
		int end = source.indexOf("\"\"\";", start + signature.length());
		assertThat(end).describedAs("상수 끝을 찾지 못했습니다: %s", name).isNotNegative();
		return source.substring(start, end);
	}

	private String read(Path path) {
		try { return Files.readString(path); }
		catch (IOException exception) { throw new IllegalStateException(exception); }
	}
}
