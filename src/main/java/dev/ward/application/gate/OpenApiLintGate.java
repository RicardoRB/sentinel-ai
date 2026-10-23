package dev.ward.application.gate;

import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.ProjectFiles;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Lints the committed OpenAPI spec with vacuum, refusing every way to silence a rule. */
final class OpenApiLintGate implements QualityGate {
  static final String VACUUM_CONFIG = "vacuum.conf.yaml";
  static final String INLINE_IGNORE = "x-lint-ignore";
  private static final Logger LOGGER = Logger.getLogger(OpenApiLintGate.class.getName());

  private final String name;
  private final CommandExecutor executor;
  private final ProjectFiles files;
  private final List<String> command;
  private final String spec;

  OpenApiLintGate(
      final String name,
      final CommandExecutor executor,
      final ProjectFiles files,
      final List<String> command,
      final String spec) {
    this.name = name;
    this.executor = executor;
    this.files = files;
    this.command = List.copyOf(command);
    this.spec = spec;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(final Project project) {
    final String refusal = refusal(project);
    if (refusal != null) {
      LOGGER.log(Level.INFO, () -> "event=openapi-lint-refused gate=" + name);
      return OpenApiResults.stopped(name, GateStatus.FAILED, refusal);
    }
    return new CommandQualityGate(
            name, executor, Stream.concat(command.stream(), Stream.of(spec)).toList())
        .execute(project);
  }

  private String refusal(final Project project) {
    if (!files.exists(project.root(), spec)) {
      return OpenApiResults.missingSpec(spec);
    }
    if (files.exists(project.root(), VACUUM_CONFIG)) {
      return VACUUM_CONFIG
          + " is not allowed: vacuum loads it on its own, and it can lower severities or ignore"
          + " rules. Remove it.";
    }
    if (files.contains(project.root(), spec, INLINE_IGNORE)) {
      return "Inline lint ignores (" + INLINE_IGNORE + ") are not allowed in " + spec + ".";
    }
    return null;
  }
}
