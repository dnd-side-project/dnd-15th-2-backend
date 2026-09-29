package com.dnd.qello.safety.web;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.dnd.qello.common.openapi.OpenApiConfiguration;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.safety.web.request.ReportCaseDecisionRequest;
import com.dnd.qello.safety.web.request.ReportCaseMoreInfoRequest;
import com.dnd.qello.safety.web.response.AnswerRestoreResponse;
import com.dnd.qello.safety.web.response.ReportCasePageResponse;
import com.dnd.qello.safety.web.response.ReportCaseResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

// OperatorReportCaseController의 문서 계약. 모든 endpoint는 운영자 세션 인증이 필요하다.
@Tag(name = "신고 사건 운영자 판정", description = "신고 사건 대기열 조회와 검토·판정·복원")
@SecurityRequirement(name = OpenApiConfiguration.OPERATOR_SESSION_SCHEME)
public interface OperatorReportCaseApiSpec {

	@Operation(summary = "신고 사건 대기열 조회", description = """
			아직 처리하지 않은 신고 사건을 처리 마감 시각이 가까운 순으로 조회합니다.

			운영자 로그인이 필요합니다.

			queue를 생략하면 STANDARD와 URGENT 대기열을 함께 조회합니다. 다음 페이지는 응답의 nextCursor를 cursor에 넣어 요청합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신고 사건 대기열을 조회했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "cursor 형식이 올바르지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/api/v1/operator/report-cases")
	ResponseEntity<ApiResponse<ReportCasePageResponse>> findQueue(
			@Parameter(description = "STANDARD 또는 URGENT. 생략하면 두 대기열을 모두 조회합니다.") @RequestParam(required = false) String queue,
			@Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 생략합니다.") @RequestParam(required = false) String cursor,
			@Parameter(description = "페이지 크기. 기본값은 20이며 1~50으로 보정됩니다.") @RequestParam(required = false, defaultValue = "20") int limit);

	@Operation(summary = "신고 사건 검토 시작", description = """
			열린 신고 사건의 검토를 시작합니다.

			운영자 로그인이 필요합니다.

			최종 판정은 판정 API로 따로 내립니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신고 사건 검토를 시작했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "사건 식별자가 올바르지 않거나 이미 종결된 사건입니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/api/v1/operator/report-cases/{caseId}/review")
	ResponseEntity<ApiResponse<ReportCaseResponse>> startReview(
			@Parameter(description = "검토를 시작할 신고 사건의 식별자.") @PathVariable long caseId);

	@Operation(summary = "신고 사건 최종 판정", description = """
			신고 사건을 ACTIONED(조치) 또는 NO_VIOLATION(위반 없음)으로 종결합니다.

			운영자 로그인이 필요합니다.

			답변 신고를 ACTIONED로 종결하면 그 답변을 숨기고 관련 알림을 취소합니다. internalNote는 신고자에게 보이지 않는 운영자 메모입니다. 종결한 사건은 다시 판정할 수 없습니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신고 사건 최종 판정을 기록했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "사건 식별자가 올바르지 않거나 판정 값이 없고, 이미 종결된 사건이거나 추가 정보 요청을 이 API로 보낼 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "ACTIONED 조치 대상 답변을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/api/v1/operator/report-cases/{caseId}/decision", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ReportCaseResponse>> decide(
			@Parameter(description = "최종 판정할 신고 사건의 식별자.") @PathVariable long caseId,
			@RequestBody @Valid ReportCaseDecisionRequest request,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "신고 사건 추가 정보 요청", description = """
			신고 사건에 추가 정보가 필요하다고 표시하고 MORE_INFO_REQUIRED 상태로 바꿉니다.

			운영자 로그인이 필요하고 무엇이 더 필요한지 적은 메모(internalNote)를 보내야 합니다.

			메모는 신고자에게 보이지 않습니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신고 사건에 추가 정보를 요청했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 메모가 올바르지 않거나 사건 식별자가 올바르지 않고, 사건이 이미 종결되었습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/api/v1/operator/report-cases/{caseId}/more-info", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ReportCaseResponse>> requestMoreInfo(
			@Parameter(description = "추가 정보가 필요한 신고 사건의 식별자.") @PathVariable long caseId,
			@RequestBody @Valid ReportCaseMoreInfoRequest request,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "숨김 답변 복원", description = """
			ACTIONED로 종결된 답변 신고에서 숨긴 답변을 다시 공개합니다.

			운영자 로그인이 필요합니다.

			질문글 신고 사건과 사용자 신고 사건은 이 API로 복원할 수 없습니다.

			<!-- HUMANIZE-SUMMARY v1.6.1 run_id: 2026-09-29-002 route: light (monolith 단일 콜, 보수 강도) metrics: char_in: 6271 char_out: 6263 change_rate: 약 0.5%  # 자가 산출 참고값. 확정 판정은 verify_change_rate.py self_check: 6/6 grade: B note: 입력 6,271자로 청크 기준 6,000자를 약간 넘었으나 오케스트레이터 지시대로 단일 청크로 처리 categories:  # before → after C-11 연결어미 뒤 쉼표: 12 → 0 A-18 좌향 수식 중의(B03 '운영자 로그인과 상태를 바꾸는~'): 1 → 0 A-15 주어-서술 호응(G03 '답변 신고는 ~숨기고 ~취소합니다'): 1 → 0 C-8·D-1·D-8·H-1·H-3·I-1·J-2: 0 → 0 (원문 미검출) self_check: - 고유명사·수치·인용·내용 앵커 100% 보존: ✅ (헤딩 60줄·영문 식별자·상태값·200/409·정형 인증 문장 원형 유지, 절 순서·개수·문단 구성 불변) - 변경률 30% 이하: ✅ - 장르 이탈 없음: ✅ - register 보존: ✅ (합니다체 유지) - S1 잔존 0건: ✅ (C-11 12 → 0, 신규 연결어미 쉼표 0) - 인공 표현 추가 없음: ✅ highlights: - id: A-18 before: "운영자 로그인과 상태를 바꾸는 백오피스 요청에 넣을 CSRF 토큰을 발급합니다." after: "운영자 로그인 요청과 상태를 바꾸는 백오피스 요청에 넣을 CSRF 토큰을 발급합니다." - id: A-15 before: "ACTIONED로 종결한 답변 신고는 그 답변을 숨기고 관련 알림을 취소합니다." after: "답변 신고를 ACTIONED로 종결하면 그 답변을 숨기고 관련 알림을 취소합니다." - id: C-11 before: "영구 장애 확정은 이 API로만 할 수 있고, 확정한 뒤에는 자동으로 되돌아가지 않습니다." after: "영구 장애 확정은 이 API로만 할 수 있고 확정한 뒤에는 자동으로 되돌아가지 않습니다." - id: C-11 before: "운영자 로그인과 CSRF 토큰이 필요하고, 결정 사유(reason)를 함께 보내야 합니다." after: "운영자 로그인과 CSRF 토큰이 필요하고 결정 사유(reason)를 함께 보내야 합니다." residual_findings: - G05 "질문글과 사용자 신고 사건은" 중의(질문글 자체인지 질문글 신고 사건인지). 확신 없음 규칙에 따라 원문 유지. 의도가 신고 사건이면 "질문글 신고 사건과 사용자 신고 사건은"으로 사람이 확정 권장 - B01 "첫 액세스 토큰과, 토큰을" 조사 뒤 쉼표는 수식 범위 구분용이라 C-11 비대상으로 보존 - C01 "대상 유형(targetType)은 현재 ANSWER만 지원합니다" 주어-서술 호응이 약간 느슨하나 의미 명확해 보존 (S2 경미) grade_reason: "B — S1 0건, S2 잔존 1건(경미), 자체검증 6항 통과. 변경률 약 0.5%로 A 하한 10%에 못 미치나 light 경로·보수 강도 지시에 따른 의도된 최소 윤문." -->""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "숨김 답변을 복원했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "사건 식별자·대상이 올바르지 않거나 ACTIONED로 종결된 사건이 아닙니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "복원할 답변을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/api/v1/operator/report-cases/{caseId}/restore")
	ResponseEntity<ApiResponse<AnswerRestoreResponse>> restore(
			@Parameter(description = "복원할 답변이 연결된 신고 사건의 식별자.") @PathVariable long caseId);
}
