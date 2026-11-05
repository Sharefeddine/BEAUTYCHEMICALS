package com.chemmanager.dto;

/** Credentials posted to {@code /auth/login}. */
public record LoginRequest(String username, String password, String locale) {
}
