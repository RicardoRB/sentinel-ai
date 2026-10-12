package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class OpenSpecGuidanceTest {
  private final String content = OpenSpecGuidance.content();

  @Test
  void loadsFromClasspathWithFrontmatterFirstAndOwnershipMarker() {
    assertThat(content).startsWith("---\nname: " + OpenSpecGuidance.SKILL_NAME + "\n");
    assertThat(content).contains("\ndescription: ");
    final int frontmatterEnd = content.indexOf("\n---\n", 4);
    assertThat(frontmatterEnd).isPositive();
    assertThat(content.indexOf(OpenSpecGuidance.MARKER)).isGreaterThan(frontmatterEnd);
  }

  @Test
  void namesOfficialWorkflowsAndLifecycleInOrder() {
    assertThat(content)
        .contains("/opsx:propose", "/opsx:apply", "/opsx:verify", "/opsx:archive", "/opsx:update")
        .contains("/opsx-propose", "/opsx-verify");
    assertThat(content)
        .containsSubsequence(
            "**Propose.**",
            "**Human review.**",
            "**Implement.**",
            "**Sentinel check.**",
            "**Advisory review.**",
            "**Human-approved archive.**");
    assertThat(content).contains("Ask a human for explicit approval, then run `/opsx:archive`");
  }

  @Test
  void separatesDeterministicGatesFromAdvisoryReview() {
    assertThat(content)
        .contains("`sentinel check` is deterministic")
        .contains("`/opsx:verify` is an advisory review")
        .contains("never replaces, overrides, or satisfies a Sentinel gate")
        .contains("never authorizes archiving on its own")
        .contains("If `/opsx:verify` is not installed, `sentinel check` still applies");
  }

  @Test
  void definesApprovalAndProhibitsWeakeningApprovedSpecs() {
    assertThat(content)
        .contains("approved once a human has approved the plan or implementation has")
        .contains("Main specifications under `openspec/specs/` are always approved")
        .contains("Never change an approved specification to make failing code or tests pass")
        .contains("only after they explicitly approve the revision");
  }

  @Test
  void bundlesNoOpenSpecTemplatesOrModelCalls() {
    assertThat(content)
        .doesNotContain("## Why", "## What Changes", "## Capabilities", "## Impact")
        .doesNotContain("### Requirement:", "#### Scenario:", "- [ ]", "<!--  ");
    assertThat(content.toLowerCase(Locale.ROOT))
        .doesNotContain("api key", "anthropic", "openai", "curl ", "http://", "https://");
  }

  @Test
  void bundledResourceIsRegisteredForNativeImage() throws IOException {
    final String metadata;
    try (InputStream input =
        OpenSpecGuidance.class.getResourceAsStream(
            "/META-INF/native-image/dev.sentinel/sentinel-cli/reachability-metadata.json")) {
      assertThat(input).isNotNull();
      metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    assertThat(metadata).contains("\"glob\": \"" + OpenSpecGuidance.RESOURCE.substring(1) + "\"");
  }
}
