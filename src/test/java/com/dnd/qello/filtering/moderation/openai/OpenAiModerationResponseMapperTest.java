/*
 * Created at: 2026-08-11T21:45:00+09:00
 * Source scenario: TEST-PLAN-GH-105-MODERATION-PIPELINE-UNIT-011,
 * TEST-PLAN-GH-105-MODERATION-PIPELINE-UNIT-012
 */
package com.dnd.qello.filtering.moderation.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.moderation.ModerationProviderResult;
import com.fasterxml.jackson.databind.ObjectMapper;

class OpenAiModerationResponseMapperTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	// DESIGN.md 결정 8에 인용된 OpenAI Moderation API 공식 응답 형태(flagged,
	// categories, category_scores, model)를 그대로 본뜬 고정 JSON. 실제 API 키·계정
	// 정보는 포함하지 않는다.
	private static final String OFFICIAL_SHAPE_JSON = """
		{
		  "id": "modr-test-001",
		  "model": "omni-moderation-2024-09-26",
		  "results": [
		    {
		      "flagged": true,
		      "categories": {
		        "harassment": true,
		        "harassment/threatening": false,
		        "hate": false
		      },
		      "category_scores": {
		        "harassment": 0.87,
		        "harassment/threatening": 0.01,
		        "hate": 0.001
		      }
		    }
		  ]
		}
		""";

	@Test
	@DisplayName("공식 응답 형태의 flagged·categories·category_scores·model이 벤더 중립 결과로 정확히 매핑된다")
	void mapsOfficialResponseShapeExactly() throws Exception {
		OpenAiModerationResponse response = objectMapper.readValue(OFFICIAL_SHAPE_JSON, OpenAiModerationResponse.class);

		ModerationProviderResult result = OpenAiModerationResponseMapper.toProviderResult(response);

		assertThat(result.flagged()).isTrue();
		assertThat(result.actualModel()).isEqualTo("omni-moderation-2024-09-26");
		assertThat(result.categories()).containsEntry("harassment", true).containsEntry("hate", false);
		assertThat(result.categoryScores()).containsEntry("harassment", 0.87);
	}

	@Test
	@DisplayName("문서화되지 않은 최상위·카테고리 필드가 추가돼도 예외 없이 알려진 필드만 매핑된다")
	void toleratesUndocumentedFields() throws Exception {
		String jsonWithUnknownFields = """
			{
			  "id": "modr-test-002",
			  "model": "omni-moderation-2024-09-26",
			  "category_applied_input_types": { "harassment": ["text"] },
			  "results": [
			    {
			      "flagged": false,
			      "categories": {
			        "harassment": false,
			        "future_category_2027": true
			      },
			      "category_scores": {
			        "harassment": 0.01,
			        "future_category_2027": 0.42
			      },
			      "future_result_field": "ignored"
			    }
			  ]
			}
			""";

		OpenAiModerationResponse response = objectMapper.readValue(jsonWithUnknownFields, OpenAiModerationResponse.class);
		ModerationProviderResult result = OpenAiModerationResponseMapper.toProviderResult(response);

		assertThat(result.flagged()).isFalse();
		assertThat(result.actualModel()).isEqualTo("omni-moderation-2024-09-26");
		assertThat(result.categories()).containsEntry("future_category_2027", true);
		assertThat(result.categoryScores()).containsEntry("future_category_2027", 0.42);
	}
}
