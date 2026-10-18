package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.init.RuleFileChange;
import dev.sentinel.domain.init.RuleFileGeneration;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/** Writes ownership-marked Maven rule files for a quality preset. */
public final class PresetRuleFileWriter implements RuleFileGeneration {
  private static final String MARKER = "<!-- managed-by: sentinel preset=";

  @Inject
  public PresetRuleFileWriter() {}

  @Override
  public RuleFileChange apply(
      final Project project, final QualityPreset preset, final List<InitGateOption> gates) {
    final Path config = project.root().resolve("config");
    if (preset == null) {
      return new RuleFileChange(config, false, Map.of(), List.of());
    }
    final boolean createdDirectory = !Files.exists(config);
    final Map<Path, String> originals = new LinkedHashMap<>();
    final List<Path> preserved = new ArrayList<>();
    try {
      Files.createDirectories(config);
      for (final Map.Entry<Path, String> target : targets(config, preset, gates).entrySet()) {
        final Path file = target.getKey();
        switch (decide(file, target.getValue())) {
          case PRESERVE_USER_OWNED -> preserved.add(file);
          case CREATE, MODIFY -> {
            originals.put(file, Files.exists(file) ? Files.readString(file) : null);
            Files.writeString(file, target.getValue());
          }
          default -> {
            // Already current.
          }
        }
      }
      return new RuleFileChange(config, createdDirectory, originals, preserved);
    } catch (IOException e) {
      throw new SentinelException("Could not write preset rule files: " + e.getMessage(), e);
    }
  }

  @Override
  public List<PlannedFileChange> plan(
      final Project project, final QualityPreset preset, final List<InitGateOption> gates) {
    if (preset == null) {
      return List.of();
    }
    final List<PlannedFileChange> plan = new ArrayList<>();
    try {
      for (final Map.Entry<Path, String> target :
          targets(project.root().resolve("config"), preset, gates).entrySet()) {
        plan.add(
            new PlannedFileChange(target.getKey(), decide(target.getKey(), target.getValue())));
      }
    } catch (IOException e) {
      throw new SentinelException("Could not read preset rule files: " + e.getMessage(), e);
    }
    return plan;
  }

  /** The rule files the selected gates need under this preset, with their marked content. */
  private static Map<Path, String> targets(
      final Path config, final QualityPreset preset, final List<InitGateOption> gates) {
    final List<String> ids = gates.stream().map(InitGateOption::id).toList();
    final Map<Path, String> targets = new LinkedHashMap<>();
    if (ids.contains("pmd")) {
      targets.put(
          config.resolve("pmd-ruleset.xml"), marked(preset, PresetRuleFileContent.pmd(preset)));
    }
    if (ids.contains("checkstyle")) {
      targets.put(
          config.resolve("checkstyle.xml"),
          marked(preset, PresetRuleFileContent.checkstyle(preset)));
    }
    if (ids.contains("spotbugs") && preset == QualityPreset.STANDARD) {
      targets.put(
          config.resolve("spotbugs-exclude.xml"),
          marked(preset, PresetRuleFileContent.spotbugs(preset)));
    }
    if (ids.contains("enforcer")) {
      targets.put(
          config.resolve("enforcer-rules.xml"),
          marked(preset, PresetRuleFileContent.enforcer(preset)));
    }
    return targets;
  }

  /** Decides what writing {@code content} to {@code file} would do; shared by apply and plan. */
  private static PlannedFileChange.Action decide(final Path file, final String content)
      throws IOException {
    if (!Files.exists(file)) {
      return PlannedFileChange.Action.CREATE;
    }
    final String existing = Files.readString(file);
    if (!existing.startsWith(MARKER)) {
      return PlannedFileChange.Action.PRESERVE_USER_OWNED;
    }
    return existing.equals(content)
        ? PlannedFileChange.Action.UNCHANGED
        : PlannedFileChange.Action.MODIFY;
  }

  private static String marked(final QualityPreset preset, final String body) {
    return MARKER + preset.id() + " -->\n" + body;
  }

  @Override
  public void rollback(final RuleFileChange change) {
    if (change == null) {
      return;
    }
    try {
      for (final Map.Entry<Path, String> entry : change.originals().entrySet()) {
        if (entry.getValue() == null) {
          Files.deleteIfExists(entry.getKey());
        } else {
          Files.writeString(entry.getKey(), entry.getValue());
        }
      }
      if (change.createdDirectory() && Files.isDirectory(change.configDirectory())) {
        Files.deleteIfExists(change.configDirectory());
      }
    } catch (IOException e) {
      throw new SentinelException("Could not roll back preset rule files: " + e.getMessage(), e);
    }
  }
}
