package dev.sentinel.domain.policy;

/**
 * The outcome of one report policy.
 *
 * @param name the policy name
 * @param passed whether it passed
 * @param message a summary for the user
 */
public record PolicyResult(String name, boolean passed, String message) {}
