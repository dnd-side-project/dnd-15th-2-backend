package com.dnd.qello.account.web.response;

import java.time.Instant;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.service.ProfileImageResolver.ResolvedProfileImage;
import com.dnd.qello.account.service.ProfileService;

/**
 * 본인 프로필 응답.
 *
 * <p>버킷 이름과 storage key는 담지 않는다. 클라이언트가 필요한 것은 만료가 있는 조회 URL
 * 하나이며, private 버킷의 내부 주소는 API 경계 밖으로 나가지 않는다.
 */
public record ProfileResponse(
	long userId,
	String nickname,
	String profileImageUrl,
	Instant profileImageExpiresAt,
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
