package com.dnd.qello.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import com.dnd.qello.common.error.ApiErrorResponseFactory;
import com.dnd.qello.common.error.CommonErrorCode;
import com.dnd.qello.common.error.ConstraintExceptionMapper;
import com.dnd.qello.common.error.ConstraintMapping;
import com.dnd.qello.common.error.DomainException;
import com.dnd.qello.common.error.ErrorCode;
import com.dnd.qello.common.web.response.ApiErrorResponse;

import jakarta.validation.ConstraintViolationException;

// 모든 오류 응답이 지나는 단일 지점.
//
// 기능 예외는 자신의 ErrorCode가 정한 상태로, 그 밖의 예외는 공통 코드로 변환.
// 처리되지 않은 예외는 500으로 수렴, 내부 메시지와 스택트레이스는 로그에만 기록.
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger APP_ERROR_LOG = LoggerFactory.getLogger("APP_ERROR");
	private static final int MAX_LOG_MESSAGE_LENGTH = 1_024;
	private static final String MDC_ERROR_CODE = "errorCode";
	private static final String MDC_ERROR_TYPE = "errorType";

	private final ApiErrorResponseFactory errorResponseFactory;
	private final ConstraintExceptionMapper constraintExceptionMapper;

	public GlobalExceptionHandler(
		ApiErrorResponseFactory errorResponseFactory,
		ConstraintExceptionMapper constraintExceptionMapper
	) {
		this.errorResponseFactory = errorResponseFactory;
		this.constraintExceptionMapper = constraintExceptionMapper;
	}

	@ExceptionHandler(DomainException.class)
	public ResponseEntity<ApiErrorResponse> handleDomain(DomainException exception) {
		return respond(exception, exception.getErrorCode(), exception.getField(), exception.getReason());
	}

	// @Valid 검증 실패. MethodArgumentNotValidException도 BindException이므로 함께 처리
	@ExceptionHandler(BindException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(BindException exception) {
		FieldError fieldError = exception.getBindingResult().getFieldError();
		String field = fieldError != null ? fieldError.getField() : null;
		String reason = fieldError != null && fieldError.getDefaultMessage() != null
			? fieldError.getDefaultMessage()
			: CommonErrorCode.INVALID_INPUT.message();
		return respond(exception, CommonErrorCode.INVALID_INPUT, field, reason);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
		String field = exception.getConstraintViolations().stream()
			.findFirst()
			.map(violation -> violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : null)
			.orElse(null);
		String reason = exception.getConstraintViolations().stream()
			.findFirst()
			.map(violation -> violation.getMessage() != null
				? violation.getMessage()
				: CommonErrorCode.INVALID_INPUT.message())
			.orElse(CommonErrorCode.INVALID_INPUT.message());
		return respond(exception, CommonErrorCode.INVALID_INPUT, field, reason);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception) {
		String field = exception.getParameterName();
		return respond(exception, CommonErrorCode.MISSING_FIELD, field, field + " 파라미터는 필수입니다.");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		String field = exception.getName();
		return respond(exception, CommonErrorCode.INVALID_INPUT, field, field + " 값의 형식이 올바르지 않습니다.");
	}

	// 요청 본문 파싱 실패. 파싱 실패 위치에 사용자 입력이 포함될 수 있으므로 원인 메시지는 로그에만 기록
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException exception) {
		return respond(exception, CommonErrorCode.INVALID_INPUT, null, "요청 본문을 읽을 수 없습니다.");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
		ConstraintMapping mapping = constraintExceptionMapper.map(extractConstraintName(exception));
		return respond(exception, mapping.errorCode(), mapping.field(), mapping.errorCode().message());
	}

	// 낙관적 잠금 충돌. 같은 행을 동시에 수정한 요청 중 뒤늦은 쪽이 여기로 온다.
	// 어느 기능이든 같은 의미이므로 공통 코드로 변환한다.
	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<ApiErrorResponse> handleOptimisticLocking(OptimisticLockingFailureException exception) {
		return respond(exception, CommonErrorCode.CONFLICT, null, "다른 요청이 먼저 같은 대상을 변경했습니다.");
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
		ErrorCode errorCode = resolveErrorCode(exception.getStatusCode());
		String reason = exception.getReason() != null ? exception.getReason() : errorCode.message();
		logException(exception, errorCode, reason);
		return ResponseEntity.status(exception.getStatusCode())
			.body(errorResponseFactory.from(errorCode, null, reason));
	}

	// 기능 코드는 도메인 예외만 사용하므로, 여기 도달하는 것은 라이브러리나 JDK가 던진 예외
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
		return respond(exception, CommonErrorCode.INVALID_INPUT, null, CommonErrorCode.INVALID_INPUT.message());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnknown(Exception exception) {
		return respond(exception, CommonErrorCode.INTERNAL_ERROR, null, CommonErrorCode.INTERNAL_ERROR.message());
	}

	private ResponseEntity<ApiErrorResponse> respond(
		Throwable exception,
		ErrorCode errorCode,
		String field,
		String reason
	) {
		logException(exception, errorCode, reason);
		return ResponseEntity.status(errorCode.httpStatus())
			.body(errorResponseFactory.from(errorCode, field, reason));
	}

	private void logException(Throwable exception, ErrorCode errorCode, String message) {
		boolean serverError = errorCode.httpStatus().is5xxServerError();
		MDC.put(MDC_ERROR_CODE, errorCode.code());
		MDC.put(MDC_ERROR_TYPE, exception.getClass().getName());
		try {
			if (serverError) {
				APP_ERROR_LOG.error(safeLogMessage(message), exception);
			} else {
				APP_ERROR_LOG.warn(safeLogMessage(message));
			}
		} finally {
			MDC.remove(MDC_ERROR_CODE);
			MDC.remove(MDC_ERROR_TYPE);
		}
	}

	private String safeLogMessage(String message) {
		if (message == null || message.isBlank()) {
			return "error";
		}
		if (message.length() > MAX_LOG_MESSAGE_LENGTH) {
			return message.substring(0, MAX_LOG_MESSAGE_LENGTH) + "...";
		}
		return message;
	}

	private String extractConstraintName(DataIntegrityViolationException exception) {
		String message = exception.getMostSpecificCause() != null
			? exception.getMostSpecificCause().getMessage()
			: exception.getMessage();
		if (message == null || message.isBlank()) {
			return null;
		}
		for (String constraint : constraintExceptionMapper.knownConstraints()) {
			if (message.contains(constraint)) {
				return constraint;
			}
		}
		return null;
	}

	private ErrorCode resolveErrorCode(HttpStatusCode statusCode) {
		if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
			return CommonErrorCode.INVALID_INPUT;
		}
		if (statusCode.value() == HttpStatus.UNAUTHORIZED.value()) {
			return CommonErrorCode.UNAUTHORIZED;
		}
		if (statusCode.value() == HttpStatus.FORBIDDEN.value()) {
			return CommonErrorCode.FORBIDDEN;
		}
		if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
			return CommonErrorCode.NOT_FOUND;
		}
		if (statusCode.value() == HttpStatus.CONFLICT.value()) {
			return CommonErrorCode.CONFLICT;
		}
		return CommonErrorCode.INTERNAL_ERROR;
	}
}
