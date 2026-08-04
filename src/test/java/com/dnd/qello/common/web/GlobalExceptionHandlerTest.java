/*
 * Created at: 2026-08-04T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-51-GLOBAL-EXCEPTION-UNIT-001 through UNIT-006
 *
 * import 수가 많아 클래스 선언 위에 두면 정책 검사 범위(첫 30줄)를 벗어나므로 여기에 배치.
 */
package com.dnd.qello.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.common.error.ApiErrorResponseFactory;
import com.dnd.qello.common.error.ConstraintExceptionMapper;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

class GlobalExceptionHandlerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
			.setControllerAdvice(new GlobalExceptionHandler(
				new ApiErrorResponseFactory(), new ConstraintExceptionMapper()))
			.build();
	}

	@Test
	@DisplayName("도메인 예외는 오류 코드가 정한 상태와 code, field, reason으로 응답한다")
	void mapsDomainExceptionToItsErrorCode() throws Exception {
		mockMvc.perform(get("/probe/domain"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value("error"))
			.andExpect(jsonPath("$.message").value(AccountErrorCode.INVALID_TIMEZONE.message()))
			.andExpect(jsonPath("$.errorDetail.code").value("ACC-VAL-004"))
			.andExpect(jsonPath("$.errorDetail.field").value("timezone"))
			.andExpect(jsonPath("$.errorDetail.reason").value("timezone은 유효한 IANA ID여야 합니다"))
			.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	@DisplayName("같은 기능이라도 오류 코드에 따라 상태가 달라진다")
	void mapsConflictErrorCodeToConflictStatus() throws Exception {
		mockMvc.perform(get("/probe/conflict"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorDetail.code").value("DIR-APP-003"));
	}

	@Test
	@DisplayName("처리되지 않은 예외는 500으로 수렴하고 내부 메시지를 응답에 노출하지 않는다")
	void hidesInternalDetailsForUnhandledException() throws Exception {
		mockMvc.perform(get("/probe/unknown"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.errorDetail.code").value("CMN-INFRA-001"))
			.andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
			.andExpect(jsonPath("$.errorDetail.reason").value("서버 내부 오류가 발생했습니다."));
	}

	@Test
	@DisplayName("요청 본문 검증 실패는 어떤 필드가 왜 실패했는지 응답에 담는다")
	void reportsFailedFieldForValidationError() throws Exception {
		mockMvc.perform(post("/probe/validated")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nickname\":\" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("CMN-VAL-001"))
			.andExpect(jsonPath("$.errorDetail.field").value("nickname"))
			.andExpect(jsonPath("$.errorDetail.reason").value("nickname은 필수입니다"));
	}

	@Test
	@DisplayName("필수 파라미터 누락과 타입 불일치는 각각의 공통 코드로 응답한다")
	void mapsRequestBindingFailures() throws Exception {
		mockMvc.perform(get("/probe/parameter"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("CMN-VAL-002"))
			.andExpect(jsonPath("$.errorDetail.field").value("size"));

		mockMvc.perform(get("/probe/parameter").param("size", "not-a-number"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("CMN-VAL-001"))
			.andExpect(jsonPath("$.errorDetail.field").value("size"));
	}

	@Test
	@DisplayName("DB 유일성 제약 위반은 제약 이름으로 기능 오류 코드를 찾아 409로 응답한다")
	void mapsKnownConstraintToFeatureErrorCode() throws Exception {
		mockMvc.perform(get("/probe/constraint"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorDetail.code").value("DIR-INFRA-001"))
			.andExpect(jsonPath("$.errorDetail.field").value("idempotencyKey"));
	}

	@Test
	@DisplayName("매핑되지 않은 제약 위반은 공통 충돌 코드로 떨어진다")
	void fallsBackToCommonConflictForUnknownConstraint() throws Exception {
		mockMvc.perform(get("/probe/constraint-unknown"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorDetail.code").value("CMN-DOM-003"));
	}

	@RestController
	static class ProbeController {

		@org.springframework.web.bind.annotation.GetMapping("/probe/domain")
		void domain() {
			throw new AccountException(
				AccountErrorCode.INVALID_TIMEZONE, "timezone", "timezone은 유효한 IANA ID여야 합니다");
		}

		@org.springframework.web.bind.annotation.GetMapping("/probe/conflict")
		void conflict() {
			throw new DirectionException(DirectionErrorCode.PRESENCE_NOT_CURRENT, "senderId");
		}

		@org.springframework.web.bind.annotation.GetMapping("/probe/unknown")
		void unknown() {
			throw new RuntimeException("연결 문자열 host=db-internal password=secret");
		}

		@PostMapping("/probe/validated")
		void validated(@RequestBody @Valid ProbeRequest request) {
			// 검증 통과 시 본문 불필요
		}

		@org.springframework.web.bind.annotation.GetMapping("/probe/parameter")
		void parameter(@RequestParam int size) {
			// 바인딩 실패만 확인
		}

		@org.springframework.web.bind.annotation.GetMapping("/probe/constraint")
		void constraint() {
			throw new DataIntegrityViolationException(
				"ERROR: duplicate key value violates unique constraint \"uq_direction_post_idempotency\"");
		}

		@org.springframework.web.bind.annotation.GetMapping("/probe/constraint-unknown")
		void constraintUnknown() {
			throw new DataIntegrityViolationException(
				"ERROR: duplicate key value violates unique constraint \"uq_unmapped_something\"");
		}
	}

	record ProbeRequest(@NotBlank(message = "nickname은 필수입니다") String nickname) {
	}
}
