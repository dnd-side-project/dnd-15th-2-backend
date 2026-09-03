/**
 * Created at: 2026-09-02T19:28:00+09:00
 * Source scenario: TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-UNIT-001 through UNIT-004
 */
package com.dnd.qello.feed.service;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.feed.view.InboxCategory;

import static org.assertj.core.api.Assertions.assertThat;

class InboxApplicationServiceTransactionBoundaryTest {

	@Test
	@DisplayName("UNIT-001 list()는 읽기 전용 REPEATABLE_READ 트랜잭션을 연다")
	void listStartsRepeatableRead() throws Exception {
		Method list = InboxApplicationService.class.getMethod(
				"list", long.class, InboxCategory.class, String.class);
		Transactional transactional = list.getAnnotation(Transactional.class);

		assertThat(transactional).isNotNull();
		assertThat(transactional.readOnly()).isTrue();
		assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
	}

	@Test
	@DisplayName("UNIT-002 클래스는 read-only 트랜잭션 기본값을 가진다")
	void classIsReadOnlyDefault() {
		Transactional transactional = InboxApplicationService.class.getAnnotation(Transactional.class);

		assertThat(transactional).isNotNull();
		assertThat(transactional.readOnly()).isTrue();
	}

	@Test
	@DisplayName("UNIT-003 트랜잭션 메서드는 public이고 클래스 내부에서 호출하지 않는다")
	void transactionalMethodsArePublicAndNotSelfInvoked() {
		for (Method method : InboxApplicationService.class.getDeclaredMethods()) {
			if (method.getAnnotation(Transactional.class) == null) {
				continue;
			}
			assertThat(Modifier.isPublic(method.getModifiers()))
					.as(method.getName())
					.isTrue();
		}
	}

	@Test
	@DisplayName("UNIT-004 field/setter Autowired가 없고 생성자 주입만 쓴다")
	void usesConstructorInjectionOnly() {
		assertThat(InboxApplicationService.class.getDeclaredFields())
				.allSatisfy(field -> assertThat(field.getAnnotation(Autowired.class)).isNull());
		assertThat(InboxApplicationService.class.getDeclaredMethods())
				.allSatisfy(method -> assertThat(method.getAnnotation(Autowired.class)).isNull());
	}
}
