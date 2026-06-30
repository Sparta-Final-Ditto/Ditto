package com.sparta.ditto.chat.domain.message;

public enum MessageType {
    TEXT(false),
    IMAGE(false),
    SYSTEM_JOIN(true),
    SYSTEM_LEAVE(true),
    SYSTEM_INVITE(true),
    SYSTEM_KICK(true);

    private final boolean system;

    MessageType(boolean system) {
        this.system = system;
    }

    public boolean isSystem() {
        return system;
    }

    public boolean isUser() {
        return !system;
    }
}
