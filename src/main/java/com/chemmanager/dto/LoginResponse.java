package com.chemmanager.dto;

import com.chemmanager.model.User;

/** Successful login payload: bearer token, profile and expiry. */
public record LoginResponse(String token, User user, String expiresAt, String dataSource) {
}
