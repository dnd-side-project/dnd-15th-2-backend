package com.dnd.qello.account.web.response;

import java.time.Instant;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.service.ProfileImageResolver.ResolvedProfileImage;
import com.dnd.qello.account.service.ProfileService;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 본인 프로필 응답.
 *
 * <p>버킷 이름과 storage key는 담지 않는다. 클라이언트가 필요한 것은 만료가 있는 조회 URL
 * 하나이며, private 버킷의 내부 주소는 API 경계 밖으로 나가지 않는다.
 */
public record ProfileResponse(
	@Schema(description = "프로필 소유자의 식별자.")
	long userId,
	@Schema(description = "현재 닉네임.")
	String nickname,
	@Schema(description = "일정 시간이 지나면 만료되는 프로필 이미지 조회 URL.")
	String profileImageUrl,
	@Schema(description = "프로필 이미지 조회 URL이 만료되는 시각.")
	Instant profileImageExpiresAt,
	@Schema(description = "프로필 이미지가 없거나 사용할 수 없어 기본 이미지를 쓰는지 여부.")
	boolean usesDefaultProfileImage
) {
	public static ProfileResponse from(ProfileService.Profile profile) {
		Account account = profile.account();
		ResolvedProfileImage image = profile.profileImage();
		return new ProfileResponse(
			account.getId(),
			account.getNickname(),
			image.url().toExternalForm(),
			image.expiresAt(),
			image.usesDefaultImage());
	}
}
