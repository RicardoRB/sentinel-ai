package dev.sentinel.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenSpecGuidanceLayoutTest {
  @Test
  void coversEverySupportedAgentOutsideOpenSpecNamespace() {
    assertThat(OpenSpecGuidanceLayout.all())
        .extracting(OpenSpecGuidanceLayout::agentId)
        .containsExactly("claude-code", "opencode");
    for (final OpenSpecGuidanceLayout layout : OpenSpecGuidanceLayout.all()) {
      assertThat(layout.guidanceFile())
          .endsWith("/skills/" + OpenSpecGuidanceLayout.SKILL_NAME + "/SKILL.md")
          .doesNotContain("opsx");
      assertThat(OpenSpecGuidanceLayout.SKILL_NAME).doesNotStartWith("openspec");
      assertThat(layout.verifyWorkflowFiles()).hasSize(2).allMatch(path -> path.contains("verify"));
    }
    assertThat(OpenSpecGuidanceLayout.forAgent("unknown")).isEmpty();
  }
}
