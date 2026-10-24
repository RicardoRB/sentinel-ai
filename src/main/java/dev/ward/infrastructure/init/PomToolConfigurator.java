package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.CompatibilityInspection;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import dev.ward.infrastructure.init.PomTools.Tool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;

/** Adds only missing, pinned Maven tool declarations required by selected init gates. */
public final class PomToolConfigurator implements BuildToolConfiguration {
  private final Optional<CompatibilityFindings> compatibility;

  /**
   * Creates the configurator; {@code compatibilityInspection} reads the toolchain to choose
   * compatible plugin versions.
   */
  @Inject
  public PomToolConfigurator(final CompatibilityInspection compatibilityInspection) {
    this.compatibility = Optional.of(new CompatibilityFindings(compatibilityInspection));
  }

  /** Legacy construction retained for focused configurator fixtures. */
  public PomToolConfigurator() {
    this.compatibility = Optional.empty();
  }

  @Override
  public BuildFileChange apply(final Project project, final List<InitGateOption> gates) {
    return apply(project, gates, null);
  }

  @Override
  public BuildFileChange apply(
      final Project project, final List<InitGateOption> gates, final QualityPreset preset) {
    return apply(project, gates, preset, finding -> false);
  }

  @Override
  public BuildFileChange apply(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Predicate<? super CompatibilityFinding> approval) {
    final PomEdit edit = compute(project, gates, preset, approval);
    if (!edit.tools.isEmpty()) {
      try {
        Files.writeString(
            edit.pom, edit.content, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
      } catch (IOException e) {
        throw new WardException(
            "Could not configure Maven tools in " + edit.pom + ": " + e.getMessage(), e);
      }
    }
    return new BuildFileChange(Toolchain.JVM, edit.pom, edit.original, edit.tools, edit.warnings);
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved) {
    final List<CompatibilityFinding> pending = new ArrayList<>();
    final PomEdit edit =
        compute(
            project,
            gates,
            preset,
            finding -> {
              if (!approved) {
                pending.add(finding);
              }
              return approved;
            });
    final List<String> details = new ArrayList<>(edit.details);
    pending.forEach(
        finding ->
            details.add(
                "~ "
                    + finding.artifactId()
                    + " "
                    + finding.currentVersion()
                    + " -> "
                    + finding.selectedVersion()
                    + " (requires approval)"));
    return new PlannedFileChange(
        edit.pom,
        details.isEmpty() ? PlannedFileChange.Action.UNCHANGED : PlannedFileChange.Action.MODIFY,
        details);
  }

  /**
   * Computes and validates the edited pom in memory; shared by {@link #apply} and {@link #plan}.
   */
  private PomEdit compute(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Predicate<? super CompatibilityFinding> approval) {
    final Path pom = project.root().resolve("pom.xml");
    final Set<String> selected = gates.stream().map(InitGateOption::id).collect(Collectors.toSet());
    final PomEdit edit;
    try {
      edit = new PomEdit(pom, Files.readString(pom));
    } catch (IOException e) {
      throw new WardException(
          "Could not configure Maven tools in " + pom + ": " + e.getMessage(), e);
    }
    final Map<String, CompatibilityFinding> findings =
        compatibility
            .map(
                resolver ->
                    resolver.resolve(
                        project,
                        PomTools.TOOLS.stream()
                            .filter(tool -> selected.contains(tool.gate()))
                            .map(Tool::artifact)
                            .toList()))
            .orElse(Map.of());
    for (final Tool tool : PomTools.TOOLS) {
      if (selected.contains(tool.gate())) {
        applySelectedTool(tool, preset, findings.get(tool.artifact()), approval, edit);
      }
    }
    edit.content = PresetPluginSnippets.pinLifecycle(edit.content, selected, preset, edit.details);
    if (!edit.tools.isEmpty()) {
      PomDeclarationValidator.validate(
          edit.content, addedDeclarations(edit.original, edit.content, selected));
    }
    return edit;
  }

  @Override
  public void rollback(final BuildFileChange change) {
    if (change == null || !change.changed()) {
      return;
    }
    try {
      Files.writeString(
          change.file(),
          change.originalContent(),
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
    } catch (IOException e) {
      throw new WardException("Could not restore " + change.file() + ": " + e.getMessage(), e);
    }
  }

  private void applySelectedTool(
      final Tool tool,
      final QualityPreset preset,
      final CompatibilityFinding finding,
      final Predicate<? super CompatibilityFinding> approval,
      final PomEdit edit) {
    if (finding != null && !containsArtifact(edit.content, tool.artifact())) {
      edit.add(tool, preset, finding.selectedVersion());
    } else if (finding != null && finding.changesVersion()) {
      final boolean accepted = approval.test(finding);
      if (accepted) {
        edit.updateVersion(tool.artifact(), finding);
      } else {
        edit.warnings.add(
            "Compatibility update declined for "
                + tool.artifact()
                + "; selected gate was not reconfigured.");
      }
    } else if (containsArtifact(edit.content, tool.artifact())) {
      if (preset != null && tool.presetSnippet() != null) {
        edit.warnings.add(
            tool.artifact() + " already declared; " + preset.id() + " settings not applied");
      }
    } else {
      edit.add(tool, preset, null);
    }
  }

  /** Declarations for the selected tools that this run added to the pom. */
  private static List<PomDeclarationValidator.Declaration> addedDeclarations(
      final String original, final String updated, final Set<String> selected) {
    return PomTools.TOOLS.stream()
        .filter(tool -> selected.contains(tool.gate()))
        .filter(tool -> !containsArtifact(original, tool.artifact()))
        .filter(tool -> containsArtifact(updated, tool.artifact()))
        .map(tool -> new PomDeclarationValidator.Declaration(tool.artifact(), tool.dependency()))
        .toList();
  }

  private static boolean containsArtifact(final String pom, final String artifactId) {
    return pom.contains("<artifactId>" + artifactId + "</artifactId>");
  }

  /** The pom being edited in memory, with what changed so far. */
  private static final class PomEdit {
    private final Path pom;
    private final String original;
    private final List<String> tools = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> details = new ArrayList<>();
    private String content;

    PomEdit(final Path pom, final String original) {
      this.pom = pom;
      this.original = original;
      this.content = original;
    }

    void add(final Tool tool, final QualityPreset preset, final String selectedVersion) {
      content = tool.addTo(content, preset, selectedVersion);
      tools.add(
          selectedVersion == null ? tool.artifact() : tool.artifact() + " " + selectedVersion);
      details.add("+ " + tool.artifact() + " " + tool.version(preset, selectedVersion));
    }

    void updateVersion(final String artifact, final CompatibilityFinding finding) {
      final Optional<String> updated =
          PomXml.replacePluginVersion(
              content, artifact, finding.currentVersion(), finding.selectedVersion());
      if (updated.isEmpty()) {
        warnings.add(
            "Compatibility update skipped for "
                + artifact
                + " because it is not declared in the project-level build; selected gate was not"
                + " reconfigured.");
        return;
      }
      final String change = finding.currentVersion() + " -> " + finding.selectedVersion();
      content = updated.get();
      tools.add(artifact + " " + change);
      details.add("~ " + artifact + " " + change);
    }
  }
}
