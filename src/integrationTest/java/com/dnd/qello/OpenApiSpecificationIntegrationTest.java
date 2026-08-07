/**
 * Created at: 2026-08-07T22:20:16+09:00
 * Source scenario: TEST-PLAN-GH-82-OPENAPI-SPEC-INT-001 through INT-004
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

// OpenAPI 스펙을 저장소 산출물로 고정한다.
//
// 이 테스트는 검증과 생성을 함께 한다. springdoc이 만든 스펙을 docs/api/openapi.json으로
// 쓰고, CI는 재생성 결과가 커밋된 파일과 다르면 실패시킨다. 컨트롤러만 바꾸고 문서를
// 갱신하지 않은 변경을 그 지점에서 막는다. 근거는 docs/api-response.md에 있다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiSpecificationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Path SPECIFICATION_PATH = Path.of("docs", "api", "openapi.json");

	// 스펙 예시나 스키마 이름에 실제 비밀값이 새어 나오는지 본다. springdoc은 필드
	// 이름만 노출하지만, 누군가 @Schema(example = ...)에 실제 값을 적으면 여기서 걸린다.
	private static final String[] FORBIDDEN_FRAGMENTS = {
		"BEGIN PRIVATE KEY",
		"eyJhbGciOi"
	};

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("springdoc이 만든 스펙을 결정적 형식으로 저장소 산출물에 기록한다")
	void writesSpecificationArtifact() throws Exception {
		String generated = canonicalize(fetchSpecification());

		Files.createDirectories(SPECIFICATION_PATH.getParent());
		Files.writeString(SPECIFICATION_PATH, generated, StandardCharsets.UTF_8);

		assertThat(Files.readString(SPECIFICATION_PATH, StandardCharsets.UTF_8))
			.isEqualTo(generated);
	}

	@Test
	@DisplayName("같은 컨텍스트에서 두 번 생성해도 스펙이 바이트 단위로 같다")
	void generationIsDeterministic() throws Exception {
		String first = canonicalize(fetchSpecification());
		String second = canonicalize(fetchSpecification());

		// 순서가 흔들리면 CI의 diff 검사가 거짓 실패를 낸다.
		assertThat(second).isEqualTo(first);
	}

	@Test
	@DisplayName("스펙에 백오피스 세션과 앱 토큰 인증 방식이 모두 선언된다")
	void declaresBothSecuritySchemes() throws Exception {
		String specification = fetchSpecification();

		assertThat(specification).contains("operatorSession");
		assertThat(specification).contains("appAccessToken");
	}

	@Test
	@DisplayName("스펙에 비밀값이 예시로 노출되지 않는다")
	void neverExposesSecretValues() throws Exception {
		String specification = fetchSpecification();

		assertThat(specification).doesNotContain(FORBIDDEN_FRAGMENTS);
	}

	private String fetchSpecification() throws Exception {
		return mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
	}

	/**
	 * JSON 객체의 키 순서와 들여쓰기, 개행을 고정한다. JSON 객체는 순서가 의미를 갖지 않으므로
	 * 정렬해도 스펙이 달라지지 않고, 배열 순서는 그대로 보존된다.
	 */
	private String canonicalize(String json) throws IOException {
		ObjectMapper canonicalMapper = JsonMapper.builder()
			.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
			.build();

		// Map으로 읽어야 ORDER_MAP_ENTRIES_BY_KEYS가 적용된다. JsonNode는 삽입 순서를 유지한다.
		Map<?, ?> tree = objectMapper.readValue(json, Map.class);
		return canonicalMapper.writer(prettyPrinter()).writeValueAsString(tree) + "\n";
	}

	/**
	 * 개행을 LF로 고정한다. Jackson 기본 들여쓰기는 OS 줄바꿈을 쓰므로 그대로 두면
	 * 같은 커밋에서도 플랫폼마다 산출물이 달라져 CI diff 검사가 거짓 실패한다.
	 */
	private DefaultPrettyPrinter prettyPrinter() {
		DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
		DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
		printer.indentObjectsWith(indenter);
		printer.indentArraysWith(indenter);
		return printer;
	}

}
