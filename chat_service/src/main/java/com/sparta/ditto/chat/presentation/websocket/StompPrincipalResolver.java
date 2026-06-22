package com.sparta.ditto.chat.presentation.websocket;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.security.Principal;
import java.util.UUID;

public final class StompPrincipalResolver {

    private StompPrincipalResolver() {
    }

    public static UUID resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
