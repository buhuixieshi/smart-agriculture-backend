package com.agriculture.service;

public record CommandDispatchResult(boolean acknowledged, String message) {

    public static CommandDispatchResult sent(String message) {
        return new CommandDispatchResult(false, message);
    }

    public static CommandDispatchResult success(String message) {
        return new CommandDispatchResult(true, message);
    }
}
