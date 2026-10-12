package dev.sentinel.infrastructure.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.OpenSpecGuidanceLayout;
import dev.sentinel.domain.doctor.EnvironmentFacts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SystemEnvironmentInspectionGuidanceTest {
  @TempDir Path project;

  @Test
  void reportsOwnedGuidanceAndVerifyWorkflowPerAgent() throws IOException {
    write(
        ".claude/skills/sentinel-openspec-workflow/SKILL.md",
        OpenSpecGuidanceLayout.OWNERSHIP_MARKER);
    write(".claude/skills/openspec-verify-change/SKILL.md", "official");
    write(".opencode/skills/sentinel-openspec-workflow/SKILL.md", "user-owned guidance");

    final EnvironmentFacts facts = new SystemEnvironmentInspection().inspect(project);

    assertThat(facts.values())
        .containsEntry("openSpecGuidance.claude-code", "true")
        .containsEntry("openSpecVerifyWorkflow.claude-code", "true")
        .containsEntry("openSpecGuidance.opencode", "false")
        .containsEntry("openSpecVerifyWorkflow.opencode", "false");
  }

  private void write(final String file, final String content) throws IOException {
    final Path target = project.resolve(file);
    Files.createDirectories(target.getParent());
    Files.writeString(target, content + "\n");
  }
}
