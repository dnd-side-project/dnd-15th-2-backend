package com.dnd.qello.direction.web;

import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.common.web.AuthenticatedUserId;
import com.dnd.qello.direction.service.DirectionPresenceService;
import com.dnd.qello.direction.web.request.UpdateActiveUserPresenceRequest;
import com.dnd.qello.direction.web.response.UpdateActiveUserPresenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/direction/presence")
public class ActiveUserPresenceController implements ActiveUserPresenceApiSpec {

    private final DirectionPresenceService presenceService;
    private final ApiResponseFactory responseFactory;

    @Override
    public ResponseEntity<ApiResponse<UpdateActiveUserPresenceResponse>> update(
            UpdateActiveUserPresenceRequest request,
            Authentication authentication) {
        boolean applied = presenceService.update(authenticatedUserId(authentication), request.toCommand());
        return ResponseEntity.ok(responseFactory.success(new UpdateActiveUserPresenceResponse(applied)));
    }

    private long authenticatedUserId(Authentication authentication) {
		return AuthenticatedUserId.require(authentication);
	}
}
