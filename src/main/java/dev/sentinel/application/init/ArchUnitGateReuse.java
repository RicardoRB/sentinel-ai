package dev.sentinel.application.init;

import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Project;
import java.util.List;
import java.util.stream.Stream;

/** Rewrites the generated ArchUnit command when user-owned tests already exist. */
final class ArchUnitGateReuse {
  private ArchUnitGateReuse() {}

  static List<InitGateOption> rewrite(
      final Project project, final List<InitGateOption> gates, final List<String> tests) {
    if (tests.isEmpty()) {
      return gates;
    }
    return gates.stream()
        .map(gate -> "archunit".equals(gate.id()) ? rewrite(project, gate, tests) : gate)
        .toList();
  }

  private static InitGateOption rewrite(
      final Project project, final InitGateOption gate, final List<String> tests) {
    final String executable =
        gate.command().isEmpty() ? project.buildExecutable() : gate.command().getFirst();
    final List<String> command =
        project.buildTool() == BuildTool.MAVEN
            ? List.of(executable, "-Dtest=" + String.join(",", tests), "test")
            : Stream.concat(
                    Stream.of(executable, "test"),
                    tests.stream().flatMap(test -> Stream.of("--tests", "*" + test)))
                .toList();
    return new InitGateOption(
        gate.id(),
        gate.description() + " Reusing existing ArchUnit tests: " + String.join(", ", tests) + ".",
        command,
        gate.available(),
        gate.availabilityMessage());
  }
}
