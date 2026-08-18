/**
 * Created at: 2026-08-18T23:33:15+09:00
 * Source scenario: TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-012
 */
package com.dnd.qello.account.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.service.ProfileImageResolver.ResolvedProfileImage;
import com.dnd.qello.account.service.ProfileService;
import com.dnd.qello.account.web.response.ProfileResponse;

class ProfileResponseTest {

	// storage key와 버킷 이름은 private 버킷의 내부 주소다. 응답에 이런 이름의 필드가
	// 생기면 그 순간 API 경계 밖으로 나간다.
	private static final List<String> FORBIDDEN_COMPONENT_FRAGMENTS =
		List.of("bucket", "storagekey", "key", "objectkey", "path");

	@Test
	@DisplayName("프로필 응답에는 버킷 이름이나 storage key에 해당하는 필드가 없다")
	void doesNotExposeStorageLocation() {
		List<String> componentNames = Arrays.stream(ProfileResponse.class.getRecordComponents())
			.map(RecordComponent::getName)
			.map(name -> name.toLowerCase(Locale.ROOT))
			.toList();

		assertThat(componentNames).isNotEmpty();
		for (String name : componentNames) {
			assertThat(FORBIDDEN_COMPONENT_FRAGMENTS)
				.as("응답 필드 %s", name)
				.noneSatisfy(forbidden -> assertThat(name).contains(forbidden));
		}
	}

	@Test
	@DisplayName("프로필 응답은 조회 URL과 만료 시각, 기본 이미지 여부를 그대로 옮긴다")
	void carriesResolvedImage() throws Exception {
		Instant expiresAt = Instant.parse("2026-08-18T00:05:00Z");
		URL url = URI.create("https://example-test.invalid/media/1/image?signed").toURL();
		Account account = Account.restore(1L, AccountRole.USER, AccountStatus.ACTIVE, "KR", "KR-TEST",
			"ko-KR", "Asia/Seoul", "qello-user", null);

		ProfileResponse response = ProfileResponse.from(
			new ProfileService.Profile(account, new ResolvedProfileImage(url, expiresAt, false)));

		assertThat(response.userId()).isEqualTo(1L);
		assertThat(response.nickname()).isEqualTo("qello-user");
		assertThat(response.profileImageUrl()).isEqualTo(url.toExternalForm());
		assertThat(response.profileImageExpiresAt()).isEqualTo(expiresAt);
		assertThat(response.usesDefaultProfileImage()).isFalse();
	}
}
