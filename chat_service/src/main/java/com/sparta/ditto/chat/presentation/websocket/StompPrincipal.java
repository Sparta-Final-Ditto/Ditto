package com.sparta.ditto.chat.presentation.websocket;

import java.security.Principal;

public record StompPrincipal(String userId) implements Principal {

    @Override
    public String getName() {
        return userId;
    }
}
