package dev.sentinel.application;

import dev.sentinel.domain.project.Language;

import java.util.List;
import java.util.Map;

/** Declarative ecosystem defaults; tools remain external and are never installed by Sentinel. */
public final class LanguageGateRegistry {
    private static final Map<Language, List<String>> DEFAULTS = Map.of(
            Language.JAVA, List.of("compile", "tests", "archunit", "checkstyle", "spotbugs", "sonar"),
            Language.KOTLIN, List.of("compile", "tests"),
            Language.TYPESCRIPT, List.of("command", "tests"),
            Language.JAVASCRIPT, List.of("command", "tests"),
            Language.PYTHON, List.of("tests", "command"),
            Language.GO, List.of("tests", "command"),
            Language.RUST, List.of("tests", "command"),
            Language.CSHARP, List.of("compile", "tests"));

    public List<String> defaults(Language language) { return DEFAULTS.getOrDefault(language, List.of()); }
}
