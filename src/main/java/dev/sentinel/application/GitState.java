package dev.sentinel.application;

public record GitState(boolean repository, String branch, boolean dirty, String message) {
}
