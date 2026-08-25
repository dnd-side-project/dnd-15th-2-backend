package com.dnd.qello.notification.push.fcm;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

/**
 * FCM HTTP v1 전송에 필요한 OAuth scope만 요청한다. credential 원문과 access token은
 * 로그에 남기지 않고, token은 Google Auth cache 안의 메모리 수명으로만 유지한다.
 */
public final class GoogleCredentialsFcmAccessTokenProvider implements FcmAccessTokenProvider {

	static final String FIREBASE_MESSAGING_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

	private final GoogleCredentials credentials;

	public GoogleCredentialsFcmAccessTokenProvider(String credentialJson) {
		if (credentialJson == null || credentialJson.isBlank()) {
			throw new IllegalArgumentException("FCM credential은 비어 있을 수 없습니다");
		}
		try {
			this.credentials = GoogleCredentials
				.fromStream(new ByteArrayInputStream(credentialJson.getBytes(StandardCharsets.UTF_8)))
				.createScoped(List.of(FIREBASE_MESSAGING_SCOPE));
		} catch (IOException exception) {
			throw new IllegalStateException("FCM credential을 읽을 수 없습니다", exception);
		}
	}

	@Override
	public String accessToken() {
		try {
			credentials.refreshIfExpired();
			AccessToken issuedCredential = credentials.getAccessToken();
			if (issuedCredential == null || issuedCredential.getTokenValue() == null
				|| issuedCredential.getTokenValue().isBlank()) {
				throw new IllegalStateException("FCM access token을 발급받지 못했습니다");
			}
			return issuedCredential.getTokenValue();
		} catch (IOException exception) {
			throw new IllegalStateException("FCM access token을 새로 고칠 수 없습니다", exception);
		}
	}

}
