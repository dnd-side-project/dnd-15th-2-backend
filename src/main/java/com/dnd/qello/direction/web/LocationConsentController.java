package com.dnd.qello.direction.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.AuthenticatedUserId;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.direction.consent.LocationConsentService;
import com.dnd.qello.direction.web.request.LocationConsentRequest;
import com.dnd.qello.direction.web.response.LocationConsentResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/location-consents")
@RequiredArgsConstructor
public class LocationConsentController implements LocationConsentApiSpec {

	private final LocationConsentService consentService;
	private final ApiResponseFactory responseFactory;

	@Override
	public ResponseEntity<ApiResponse<LocationConsentResponse>> consent(
			LocationConsentRequest request, Authentication authentication) {
		return ResponseEntity.ok(responseFactory.success(LocationConsentResponse.from(
				consentService.consent(AuthenticatedUserId.require(authentication), request.termsVersion()))));
	}

	@Override
	public ResponseEntity<ApiResponse<LocationConsentResponse>> withdraw(Authentication authentication) {
		return ResponseEntity.ok(responseFactory.success(LocationConsentResponse.from(
				consentService.withdraw(AuthenticatedUserId.require(authentication)))));
	}

	@Override
	public ResponseEntity<ApiResponse<LocationConsentResponse>> current(Authentication authentication) {
		return ResponseEntity.ok(responseFactory.success(LocationConsentResponse.from(
				consentService.current(AuthenticatedUserId.require(authentication)))));
	}
}
