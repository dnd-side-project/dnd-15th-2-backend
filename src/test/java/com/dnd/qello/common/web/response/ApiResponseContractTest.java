/*
 * Created at: 2026-08-07T09:20:00+09:00
 * Source scenario: TEST-PLAN-GH-74-API-RESPONSE-UNIT-001 through UNIT-005
 *
 */
package com.dnd.qello.common.web.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.common.error.ApiErrorResponseFactory;
import com.dnd.qello.common.error.ConstraintExceptionMapper;
import com.dnd.qello.common.web.GlobalExceptionHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

class ApiResponseContractTest {

	private static final Instant FIXED_NOW = Instant.parse("2026-08-07T00:00:00Z");

	private final ObjectMapper objectMapper = new ObjectMapper();

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
		mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController(new ApiResponseFactory(clock)))
			.setControllerAdvice(new GlobalExceptionHandler(
				new ApiErrorResponseFactory(clock), new ConstraintExceptionMapper()))
			.build();
	}

	@Test
	@DisplayName("성공 응답은 status, data, timestamp만 갖는다")
	void successResponseHasFixedShape() throws Exception {
		mockMvc.perform(get("/probe/resource"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{
				  "status": "success",
				  "data": { "resourceId": 1, "name": "첫 번째" },
				  "timestamp": "2026-08-07T00:00:00Z"
				}
				""", JsonCompareMode.STRICT));
	}

	@Test
	@DisplayName("성공과 오류 응답은 status, timestamp를 공유하고 그 사이 슬롯만 갈린다")
	void successAndErrorResponsesAreSymmetric() throws Exception {
		Map<String, Object> success = readBody(get("/probe/resource"));
		Map<String, Object> error = readBody(get("/probe/failure"));

		assertThat(success.keySet()).containsExactlyInAnyOrder("status", "data", "timestamp");
		assertThat(error.keySet()).containsExactlyInAnyOrder("status", "message", "errorDetail", "timestamp");
		assertThat(success).containsEntry("status", "success");
		assertThat(error).containsEntry("status", "error");
		assertThat(success.get("timestamp")).isEqualTo(error.get("timestamp"));
	}

	@Test
	@DisplayName("생성 성공은 201과 Location 헤더를 내고 본문 형식은 그대로 유지한다")
	void createdResponseKeepsTheSameBodyShape() throws Exception {
		mockMvc.perform(get("/probe/created"))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/probe/resource/1"))
			.andExpect(jsonPath("$.status").value("success"))
			.andExpect(jsonPath("$.data.resourceId").value(1))
			.andExpect(jsonPath("$.timestamp").value(FIXED_NOW.toString()));
	}

	@Test
	@DisplayName("돌려줄 값이 없는 성공은 204가 아니라 200과 data null로 응답한다")
	void emptySuccessUsesOkWithNullData() throws Exception {
		String body = mockMvc.perform(get("/probe/no-content"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		// data 키가 사라지면 클라이언트가 존재 여부로 분기하게 되므로 null이라도 남아야 한다.
		assertThat(body).contains("\"data\":null");
		assertThat(readJson(body).keySet()).containsExactlyInAnyOrder("status", "data", "timestamp");
	}

	@Test
	@DisplayName("성공과 오류의 timestamp는 모두 Clock 빈에서 나온다")
	void timestampComesFromTheClockBean() throws Exception {
		mockMvc.perform(get("/probe/resource"))
			.andExpect(jsonPath("$.timestamp").value(FIXED_NOW.toString()));
		mockMvc.perform(get("/probe/failure"))
			.andExpect(jsonPath("$.timestamp").value(FIXED_NOW.toString()));
	}

	private Map<String, Object> readBody(MockHttpServletRequestBuilder request) throws Exception {
		return readJson(mockMvc.perform(request).andReturn().getResponse().getContentAsString());
	}

	private Map<String, Object> readJson(String body) throws Exception {
		return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
		});
	}

	@RestController
	static class ProbeController {

		private final ApiResponseFactory responseFactory;

		ProbeController(ApiResponseFactory responseFactory) {
			this.responseFactory = responseFactory;
		}

		@GetMapping("/probe/resource")
		ResponseEntity<ApiResponse<ProbeResource>> resource() {
			return ResponseEntity.ok(responseFactory.success(new ProbeResource(1L, "첫 번째")));
		}

		@GetMapping("/probe/created")
		ResponseEntity<ApiResponse<ProbeResource>> created() {
			return ResponseEntity.created(URI.create("/probe/resource/1"))
				.body(responseFactory.success(new ProbeResource(1L, "첫 번째")));
		}

		@GetMapping("/probe/no-content")
		ResponseEntity<ApiResponse<Void>> noContent() {
			return ResponseEntity.ok(responseFactory.success());
		}

		@GetMapping("/probe/failure")
		ResponseEntity<ApiResponse<ProbeResource>> failure() {
			throw new AccountException(AccountErrorCode.ACCOUNT_NOT_FOUND, "accountId");
		}
	}

	record ProbeResource(Long resourceId, String name) {
	}
}
