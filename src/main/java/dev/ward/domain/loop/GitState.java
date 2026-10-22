package dev.ward.domain.loop;

/** Git facts required by quality-loop policy. */
public record GitState(boolean repository, String branch, boolean dirty, String message) {}
