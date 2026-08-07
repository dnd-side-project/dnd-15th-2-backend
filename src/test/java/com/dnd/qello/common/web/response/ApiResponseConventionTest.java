/*
 * Created at: 2026-08-07T09:20:00+09:00
 * Source scenario: TEST-PLAN-GH-74-API-RESPONSE-CONVENTION-001 through CONVENTION-003
 *
 * import 수가 많아 클래스 선언 위에 두면 정책 검사 범위(첫 30줄)를 벗어나므로 여기에 배치.
 */
package com.dnd.qello.common.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 성공 응답을 전역 자동 래핑하지 않기로 했으므로(ADR-0005), 래핑을 빠뜨린 handler를
// 여기서 잡는다. 자동 래핑이 주는 "빠뜨릴 수 없다"를 테스트로 대신한다.
class ApiResponseConventionTest {

	private static final String BASE_PACKAGE = "com.dnd.qello";

	@Test
	@DisplayName("모든 controller의 handler는 ApiResponse 또는 ResponseEntity<ApiResponse>를 반환한다")
	void everyHandlerReturnsApiResponse() {
		List<String> violations = new ArrayList<>();
		for (Class<?> controller : mainControllers()) {
			violations.addAll(violations(controller));
		}

		assertThat(violations).isEmpty();
	}

	@Test
	@DisplayName("규약을 지킨 handler는 위반으로 잡히지 않는다")
	void acceptsCompliantHandlers() {
		assertThat(violations(CompliantProbeController.class)).isEmpty();
	}

	@Test
	@DisplayName("감싸지 않은 반환값과 void 반환은 위반으로 잡힌다")
	void rejectsUnwrappedHandlers() {
		assertThat(violations(NonCompliantProbeController.class))
			.hasSize(3)
			.anySatisfy(violation -> assertThat(violation).contains("raw"))
			.anySatisfy(violation -> assertThat(violation).contains("noBody"))
			.anySatisfy(violation -> assertThat(violation).contains("rawEntity"));
	}

	// 반환 타입 규약을 어긴 handler의 설명 목록. 어기지 않았으면 빈 목록.
	private static List<String> violations(Class<?> controller) {
		List<String> violations = new ArrayList<>();
		for (Method method : controller.getDeclaredMethods()) {
			if (method.isSynthetic() || !AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
				continue;
			}
			if (!isWrapped(method.getGenericReturnType())) {
				violations.add("%s#%s: %s".formatted(
					controller.getSimpleName(), method.getName(), method.getGenericReturnType().getTypeName()));
			}
		}
		return violations;
	}

	// ApiResponse<T> 또는 ResponseEntity<ApiResponse<T>>만 허용.
	// 본문 없는 성공도 200과 data: null로 나가므로 void는 허용하지 않는다.
	private static boolean isWrapped(Type returnType) {
		if (!(returnType instanceof ParameterizedType parameterized)) {
			return false;
		}
		Type rawType = parameterized.getRawType();
		if (rawType.equals(ApiResponse.class)) {
			return true;
		}
		if (!rawType.equals(ResponseEntity.class)) {
			return false;
		}
		Type body = parameterized.getActualTypeArguments()[0];
		return body instanceof ParameterizedType bodyType && bodyType.getRawType().equals(ApiResponse.class);
	}

	// 테스트 소스의 probe controller까지 잡히면 안 되므로 main 산출물만 고른다.
	private static List<Class<?>> mainControllers() {
		ClassPathScanningCandidateComponentProvider scanner =
			new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

		List<Class<?>> controllers = new ArrayList<>();
		for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
			Class<?> candidate = resolve(definition.getBeanClassName());
			if (candidate != null && isMainClass(candidate)) {
				controllers.add(candidate);
			}
		}
		return controllers;
	}

	private static Class<?> resolve(String className) {
		try {
			return className == null ? null : Class.forName(className);
		} catch (ClassNotFoundException exception) {
			return null;
		}
	}

	private static boolean isMainClass(Class<?> candidate) {
		if (candidate.getProtectionDomain().getCodeSource() == null) {
			return false;
		}
		URL location = candidate.getProtectionDomain().getCodeSource().getLocation();
		return location != null && location.getPath().contains("/classes/java/main/");
	}

	@RestController
	static class CompliantProbeController {

		@GetMapping("/convention/wrapped")
		ApiResponse<String> wrapped() {
			return null;
		}

		@GetMapping("/convention/wrapped-entity")
		ResponseEntity<ApiResponse<String>> wrappedEntity() {
			return null;
		}
	}

	@RestController
	static class NonCompliantProbeController {

		@GetMapping("/convention/raw")
		String raw() {
			return null;
		}

		@GetMapping("/convention/no-body")
		void noBody() {
			// 204를 쓰지 않기로 했으므로 본문 없는 handler는 규약 위반
		}

		@GetMapping("/convention/raw-entity")
		ResponseEntity<String> rawEntity() {
			return null;
		}
	}
}
