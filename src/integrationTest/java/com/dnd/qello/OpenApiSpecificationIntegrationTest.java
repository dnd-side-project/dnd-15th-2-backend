/**
 * Created at: 2026-08-07T22:20:16+09:00
 * Source scenario: TEST-PLAN-GH-82-OPENAPI-SPEC-INT-001 through INT-004,
 * TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-011 (added 2026-08-14T00:51:11+09:00),
 * TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-019 (added 2026-08-14T12:27:28+09:00),
 * TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-012 (added 2026-08-21T21:25:00+09:00),
 * TEST-PLAN-GH-179-PUSH-DELIVERY-INT-018 (added 2026-08-25T00:17:40+09:00)
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
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

	@Test
	@DisplayName("presence PUT은 앱 토큰과 승인된 요청·응답 privacy 계약을 문서화한다")
	void documentsActiveUserPresenceContract() throws Exception {
		JsonNode specification = objectMapper.readTree(fetchSpecification());
		JsonNode operation = specification.at("/paths/~1api~1v1~1direction~1presence/put");

		assertThat(operation.isMissingNode()).isFalse();
		assertThat(operation.at("/security/0/appAccessToken").isArray()).isTrue();
		assertThat(fieldNames(resolveSchema(specification,
			operation.at("/requestBody/content/application~1json/schema"))))
			.containsExactlyInAnyOrder("latitude", "longitude", "accuracyMeters", "receiveAllowed", "observedAt");
		assertThat(operation.at("/responses").fieldNames()).toIterable()
			.contains("200", "400", "401", "403", "404", "500");

		JsonNode successEnvelope = resolveSchema(specification,
			operation.at("/responses/200/content/application~1json/schema"));
		JsonNode responseData = resolveSchema(specification, successEnvelope.at("/properties/data"));
		assertThat(fieldNames(responseData)).containsExactly("applied");
	}

	@Test
	@DisplayName("direction preview는 앱 인증·200 응답·구간별 count와 privacy 계약을 문서화한다")
	void documentsDirectionPreviewContract() throws Exception {
		JsonNode specification = objectMapper.readTree(fetchSpecification());
		JsonNode operation = operation(specification, "/api/v1/direction/preview", "get");

		assertThat(operation.isMissingNode()).isFalse();
		assertAppAuthentication(operation);
		assertResponses(operation, "200", "400", "401", "409", "500");

		JsonNode successEnvelope = resolveSchema(specification,
			operation.at("/responses/200/content/application~1json/schema"));
		JsonNode responseData = resolveSchema(specification, successEnvelope.at("/properties/data"));
		assertThat(fieldNames(responseData)).containsExactlyInAnyOrder("schemeId", "schemeCode", "schemeVersion", "segments");
		JsonNode segmentSchema = resolveSchema(specification,
			responseData.at("/properties/segments/items"));
		assertThat(fieldNames(segmentSchema)).containsExactlyInAnyOrder("segmentKey", "displayName", "sortOrder", "count");
		assertNoPrivateFields(operation);
	}

	@Test
	@DisplayName("direction post 제출은 Idempotency-Key·앱 인증·202 응답과 비동기 privacy 계약을 문서화한다")
	void documentsDirectionPostSubmissionContract() throws Exception {
		JsonNode specification = objectMapper.readTree(fetchSpecification());
		JsonNode operation = operation(specification, "/api/v1/direction/posts", "post");

		assertThat(operation.isMissingNode()).isFalse();
		assertAppAuthentication(operation);
		assertThat(operation.at("/parameters").findValuesAsText("name"))
			.contains("Idempotency-Key");
		assertResponses(operation, "202", "400", "401", "403", "404", "409", "500");

		JsonNode request = resolveSchema(specification,
			operation.at("/requestBody/content/application~1json/schema"));
		assertThat(fieldNames(request)).containsExactlyInAnyOrder(
			"approvedQuestionId", "schemeId", "segmentKey", "bodyText", "mediaIds");
		JsonNode successEnvelope = resolveSchema(specification,
			operation.at("/responses/202/content/application~1json/schema"));
		JsonNode responseData = resolveSchema(specification, successEnvelope.at("/properties/data"));
		assertThat(fieldNames(responseData)).containsExactlyInAnyOrder(
			"postId", "submissionStatus", "submittedAt", "expiresAt");
		assertNoPrivateFields(operation);
	}

	@Test
	@DisplayName("media upload 예약은 앱 인증·201 응답·presigned URL만 문서화한다")
	void documentsMediaUploadContract() throws Exception {
		JsonNode specification = objectMapper.readTree(fetchSpecification());
		JsonNode operation = operation(specification, "/api/v1/media-assets/upload-requests", "post");

		assertThat(operation.isMissingNode()).isFalse();
		assertAppAuthentication(operation);
		assertResponses(operation, "201", "400", "401", "500");

		JsonNode request = resolveSchema(specification,
			operation.at("/requestBody/content/application~1json/schema"));
		assertThat(fieldNames(request)).containsExactlyInAnyOrder("contentType", "byteSize", "checksum");
		JsonNode successEnvelope = resolveSchema(specification,
			operation.at("/responses/201/content/application~1json/schema"));
		JsonNode responseData = resolveSchema(specification, successEnvelope.at("/properties/data"));
		assertThat(fieldNames(responseData)).containsExactlyInAnyOrder(
			"mediaId", "uploadUrl", "contentType", "expiresAt");
		assertThat(responseData.at("/properties/uploadUrl").path("type").asText()).isEqualTo("string");
		assertThat(fieldNames(responseData)).doesNotContain("storageKey", "userId", "latitude", "longitude");
	}

	@Test
	@DisplayName("media confirm은 앱 인증·200 응답과 storage 정보 비노출 계약을 문서화한다")
	void documentsMediaConfirmContract() throws Exception {
		JsonNode specification = objectMapper.readTree(fetchSpecification());
		JsonNode operation = operation(specification, "/api/v1/media-assets/{mediaId}/confirm", "post");

		assertThat(operation.isMissingNode()).isFalse();
		assertAppAuthentication(operation);
		assertResponses(operation, "200", "400", "401", "404", "500", "503");
		assertThat(operation.at("/parameters").findValuesAsText("name")).contains("mediaId");

		JsonNode successEnvelope = resolveSchema(specification,
			operation.at("/responses/200/content/application~1json/schema"));
		JsonNode responseData = resolveSchema(specification, successEnvelope.at("/properties/data"));
		assertThat(fieldNames(responseData)).containsExactlyInAnyOrder("mediaId", "status");
		assertNoPrivateFields(operation);
	}

	@Test
	@DisplayName("notification preference GET·PUT은 본인 전용 snapshot 계약과 6종 enum을 문서화한다")
	void documentsNotificationPreferenceContract() throws Exception {
		JsonNode specification = objectMapper.readTree(fetchSpecification());
		JsonNode getOperation = operation(specification, "/api/v1/notifications/preferences", "get");
		JsonNode putOperation = operation(specification, "/api/v1/notifications/preferences", "put");

		assertThat(getOperation.isMissingNode()).isFalse();
		assertAppAuthentication(getOperation);
		assertResponses(getOperation, "200", "401", "403", "404");

		JsonNode getSuccessEnvelope = resolveSchema(specification,
			getOperation.at("/responses/200/content/application~1json/schema"));
		JsonNode getResponseData = resolveSchema(specification, getSuccessEnvelope.at("/properties/data"));
		assertThat(fieldNames(getResponseData))
			.containsExactlyInAnyOrder("pushEnabled", "quietHours", "preferences", "inboxRecordingPolicy");
		assertThat(enumValues(getResponseData.at("/properties/inboxRecordingPolicy")))
			.containsExactly("ALWAYS_RECORD");
		JsonNode responsePreferenceItemSchema = resolveSchema(specification,
			getResponseData.at("/properties/preferences/items"));
		assertThat(enumValues(responsePreferenceItemSchema.at("/properties/type")))
			.containsExactly(
				"ANSWER_RECEIVED",
				"ANSWER_REACTED",
				"DIRECTION_POST_RECEIVED",
				"REPORT_RESOLVED",
				"QUESTION_PROPOSAL_REVIEWED",
				"QUESTION_RECOMMENDED");

		assertThat(putOperation.isMissingNode()).isFalse();
		assertAppAuthentication(putOperation);
		assertResponses(putOperation, "200", "400", "401", "403", "404");

		JsonNode requestSchema = resolveSchema(specification,
			putOperation.at("/requestBody/content/application~1json/schema"));
		assertThat(fieldNames(requestSchema))
			.containsExactlyInAnyOrder("pushEnabled", "quietHours", "preferences");
		assertThat(requiredFields(requestSchema)).containsExactlyInAnyOrder("pushEnabled", "preferences");

		JsonNode quietHoursSchema = resolveSchema(specification, requestSchema.at("/properties/quietHours"));
		assertThat(fieldNames(quietHoursSchema)).containsExactlyInAnyOrder("start", "end", "zoneId");
		assertThat(requiredFields(quietHoursSchema)).containsExactlyInAnyOrder("start", "end", "zoneId");

		JsonNode preferenceItemSchema = resolveSchema(specification,
			requestSchema.at("/properties/preferences/items"));
		assertThat(fieldNames(preferenceItemSchema)).containsExactlyInAnyOrder("type", "enabled");
		assertThat(requiredFields(preferenceItemSchema)).containsExactlyInAnyOrder("type", "enabled");
		assertThat(enumValues(preferenceItemSchema.at("/properties/type")))
			.containsExactly(
				"ANSWER_RECEIVED",
				"ANSWER_REACTED",
				"DIRECTION_POST_RECEIVED",
				"REPORT_RESOLVED",
				"QUESTION_PROPOSAL_REVIEWED",
				"QUESTION_RECOMMENDED");
		assertNoPrivateFields(getOperation);
		assertNoPrivateFields(putOperation);
	}

	@Test
	@DisplayName("INT-018: push device register와 revoke의 204 응답은 content schema를 노출하지 않는다")
	void documentsPushDeviceNoContentResponsesWithoutSchema() throws Exception {
		JsonNode specification = objectMapper.readTree(fetchSpecification());
		JsonNode registerOperation = operation(specification, "/api/v1/notifications/devices", "post");
		JsonNode revokeOperation = operation(specification, "/api/v1/notifications/devices/revoke", "post");
		JsonNode registerResponse = registerOperation
			.at("/responses/204");
		JsonNode revokeResponse = revokeOperation
			.at("/responses/204");

		assertThat(registerOperation.at("/requestBody/required").asBoolean()).isTrue();
		assertThat(revokeOperation.at("/requestBody/required").asBoolean()).isTrue();
		assertThat(registerResponse.isMissingNode()).isFalse();
		assertThat(registerResponse.has("content")).isFalse();
		assertThat(revokeResponse.isMissingNode()).isFalse();
		assertThat(revokeResponse.has("content")).isFalse();
	}

	private JsonNode operation(JsonNode specification, String path, String method) {
		// RFC 6901에서 path key의 slash만 escape한다. `{mediaId}`는 JSON Pointer에서
		// 특별한 문자가 아니므로 그대로 둬야 springdoc 경로를 찾을 수 있다.
		return specification.at("/paths/" + path.replace("/", "~1")
			+ "/" + method);
	}

	private void assertAppAuthentication(JsonNode operation) {
		assertThat(operation.at("/security/0/appAccessToken").isArray()).isTrue();
	}

	private void assertResponses(JsonNode operation, String... responseCodes) {
		for (String responseCode : responseCodes) {
			JsonNode response = operation.at("/responses/" + responseCode);
			assertThat(response.isMissingNode())
				.as("missing response %s", responseCode)
				.isFalse();
			if (responseCode.startsWith("4") || responseCode.startsWith("5")) {
				assertThat(response.at("/content/application~1json/schema/$ref").asText())
					.as("error schema for response %s", responseCode)
					.isEqualTo("#/components/schemas/ApiErrorResponse");
			}
		}
	}

	private void assertNoPrivateFields(JsonNode operation) {
		String serialized = operation.toString();
		assertThat(serialized).doesNotContain(
			"\"userId\"", "\"latitude\"", "\"longitude\"", "\"recipientId\"",
			"\"recipients\"", "\"storageKey\"");
	}

	private String fetchSpecification() throws Exception {
		return mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
	}

	private JsonNode resolveSchema(JsonNode specification, JsonNode schema) {
		String reference = schema.path("$ref").asText();
		if (reference.isBlank()) {
			return schema;
		}
		String schemaName = reference.substring(reference.lastIndexOf('/') + 1);
		return specification.path("components").path("schemas").path(schemaName);
	}

	private Set<String> fieldNames(JsonNode schema) {
		Set<String> names = new java.util.LinkedHashSet<>();
		schema.path("properties").fieldNames().forEachRemaining(names::add);
		return names;
	}

	private Set<String> requiredFields(JsonNode schema) {
		Set<String> names = new java.util.LinkedHashSet<>();
		schema.path("required").forEach(node -> names.add(node.asText()));
		return names;
	}

	private List<String> enumValues(JsonNode schema) {
		List<String> values = new java.util.ArrayList<>();
		schema.path("enum").forEach(node -> values.add(node.asText()));
		return values;
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
