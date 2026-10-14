package dev.sentinel.infrastructure.traceability;

import dev.sentinel.domain.traceability.MappingObservation;
import dev.sentinel.domain.traceability.SourceLocation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts annotations immediately preceding one Java declaration into an observation. */
final class JavaMappingObservationParser {
  private static final Pattern ANNOTATION =
      Pattern.compile("@([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*(?:\\(([^)]*)\\))?");
  private static final String SPEC_PREFIX = "spec:";
  private static final String TEST_FACTORY = "TestFactory";
  private static final String DISPLAY_NAME = "DisplayName";

  private JavaMappingObservationParser() {}

  static MappingObservation parse(final String prefix, final Path path, final int line) {
    final List<String[]> annotations = annotations(prefix);
    if (annotations.isEmpty()) {
      return null;
    }
    final MappingParts parts = readAnnotations(annotations);
    if (!parts.traceability()) {
      return null;
    }
    final String problem = problem(parts);
    final String tag = parts.tag() == null ? "" : parts.tag();
    final String display = parts.display() == null ? "" : parts.display();
    final String capability =
        tag.startsWith(SPEC_PREFIX) ? tag.substring(SPEC_PREFIX.length()) : "";
    return new MappingObservation(
        capability, display, new SourceLocation(path, line), parts.test(), problem);
  }

  private static String problem(final MappingParts parts) {
    String problem = parts.problem();
    if (!parts.test()) {
      problem = firstProblem(problem, "traceability annotation is not on a supported JUnit test");
    }
    if (parts.tag() == null || !parts.tag().startsWith(SPEC_PREFIX)) {
      problem = firstProblem(problem, "missing direct spec tag");
    }
    if (parts.display() == null || parts.display().isBlank()) {
      problem = firstProblem(problem, "missing direct display name");
    }
    return problem;
  }

  private static MappingParts readAnnotations(final List<String[]> annotations) {
    final AnnotationState state = new AnnotationState();
    for (final String[] annotation : annotations) {
      state.accept(annotation[0], annotation[1]);
    }
    return state.parts();
  }

  private static boolean isSupportedTest(final String name) {
    return "Test".equals(name) || "ParameterizedTest".equals(name) || "RepeatedTest".equals(name);
  }

  private static String firstProblem(final String current, final String next) {
    return current.isBlank() ? next : current;
  }

  private static List<String[]> annotations(final String prefix) {
    final List<String[]> result = new ArrayList<>();
    final Matcher matcher = ANNOTATION.matcher(prefix);
    while (matcher.find()) {
      result.add(new String[] {matcher.group(1), matcher.group(2)});
    }
    return result;
  }

  private static String quoted(final String value) {
    final int first = value.indexOf('"');
    final int last = value.lastIndexOf('"');
    return first >= 0 && last > first ? value.substring(first + 1, last) : "";
  }

  private static final class AnnotationState {
    private String tag;
    private String display;
    private String problem = "";
    private boolean test;
    private boolean traceability;

    private void accept(final String name, final String value) {
      final String simple = name.substring(name.lastIndexOf('.') + 1);
      if (isSupportedTest(simple)) {
        test = true;
      } else if (TEST_FACTORY.equals(simple)) {
        traceability = true;
      } else if ("Tag".equals(simple)
          && value != null
          && value.trim().startsWith("\"" + SPEC_PREFIX)) {
        traceability = true;
        if (tag != null) {
          problem = "multiple spec: tags on one method";
        }
        tag = quoted(value);
      } else if (DISPLAY_NAME.equals(simple)) {
        traceability = true;
        display = quoted(value);
      }
    }

    private MappingParts parts() {
      return new MappingParts(tag, display, test, traceability, problem);
    }
  }

  private record MappingParts(
      String tag, String display, boolean test, boolean traceability, String problem) {}
}
