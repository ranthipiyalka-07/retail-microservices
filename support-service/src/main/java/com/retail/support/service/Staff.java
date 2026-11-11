package com.retail.support.service;

/** The agent behind the request: the token's userId claim and its subject (the login email). */
public record Staff(Long userId, String email) {}