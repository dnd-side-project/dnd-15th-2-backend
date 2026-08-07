package com.dnd.qello.common.openapi;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.http.MediaType;

import com.dnd.qello.common.web.response.ApiErrorResponse;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

// 모든 엔드포인트에 공통으로 참인 문서 규칙을 한곳에서 주입한다.
//
// springdoc은 반환 타입만 보고 성공 응답을 추론하므로, 컨트롤러마다 같은 내용을 반복해
// 적지 않으려면 여기서 채워야 한다. 이 클래스가 다루는 것은 "어느 엔드포인트에서나 참인"
// 규칙뿐이다. 엔드포인트마다 다른 오류(로그인 423, 등록 409)는 각 컨트롤러의
// @ApiResponse로 적는다. 여기서 추측해 넣으면 사실과 다른 문서가 된다.
public class OpenApiConventionCustomizer implements OpenApiCustomizer {

	static final String ERROR_SCHEMA_NAME = "ApiErrorResponse";

	private static final String WILDCARD_MEDIA_TYPE = "*/*";
	private static final String ERROR_SCHEMA_REF = "#/components/schemas/" + ERROR_SCHEMA_NAME;

	@Override
	public void customise(OpenAPI openApi) {
		registerErrorSchema(openApi);

		if (openApi.getPaths() == null) {
			return;
		}
		openApi.getPaths().values().forEach(pathItem ->
			pathItem.readOperations().forEach(operation -> {
				narrowContentType(operation);
				addCommonErrorResponses(operation);
			}));
	}

	// 오류 응답 스키마는 어떤 컨트롤러도 반환 타입으로 쓰지 않는다.
	// GlobalExceptionHandler가 만들기 때문이라 springdoc이 스스로 찾지 못한다.
	private void registerErrorSchema(OpenAPI openApi) {
		if (openApi.getComponents().getSchemas() != null
			&& openApi.getComponents().getSchemas().containsKey(ERROR_SCHEMA_NAME)) {
			return;
		}

		ResolvedSchema resolved = ModelConverters.getInstance()
			.readAllAsResolvedSchema(new AnnotatedType(ApiErrorResponse.class));
		if (resolved == null) {
			return;
		}
		resolved.referencedSchemas.forEach(openApi.getComponents()::addSchemas);
	}

	// 컨트롤러가 produces를 선언하지 않으면 springdoc이 */*로 열어 둔다.
	// 이 서비스는 JSON만 반환하므로 좁힌다.
	private void narrowContentType(Operation operation) {
		ApiResponses responses = operation.getResponses();
		if (responses == null) {
			return;
		}
		responses.values().forEach(response -> {
			Content content = response.getContent();
			if (content == null || !content.containsKey(WILDCARD_MEDIA_TYPE)) {
				return;
			}
			content.addMediaType(MediaType.APPLICATION_JSON_VALUE, content.remove(WILDCARD_MEDIA_TYPE));
		});
	}

	// 400과 500은 GlobalExceptionHandler가 어떤 요청에서든 낼 수 있다.
	// 컨트롤러가 이미 명시했으면 덮어쓰지 않는다.
	private void addCommonErrorResponses(Operation operation) {
		ApiResponses responses = operation.getResponses();
		if (responses == null) {
			return;
		}
		putIfAbsent(responses, "400", "요청 값이 올바르지 않습니다. 오류 코드는 docs/error-codes.md를 따른다.");
		putIfAbsent(responses, "500", "서버 내부 오류가 발생했습니다.");
	}

	private void putIfAbsent(ApiResponses responses, String statusCode, String description) {
		if (responses.containsKey(statusCode)) {
			return;
		}
		responses.addApiResponse(statusCode, new ApiResponse()
			.description(description)
			.content(new Content().addMediaType(
				MediaType.APPLICATION_JSON_VALUE,
				new io.swagger.v3.oas.models.media.MediaType()
					.schema(new Schema<>().$ref(ERROR_SCHEMA_REF)))));
	}

}
