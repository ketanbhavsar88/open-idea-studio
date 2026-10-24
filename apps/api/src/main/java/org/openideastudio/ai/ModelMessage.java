package org.openideastudio.ai;

public record ModelMessage(Role role, String text) {

    public enum Role { USER, MODEL }

    public static ModelMessage user(String text) {
        return new ModelMessage(Role.USER, text);
    }

    public static ModelMessage model(String text) {
        return new ModelMessage(Role.MODEL, text);
    }
}
