package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationRequest.Guidance;
import dev.sentinel.domain.agent.IntegrationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OpenSpecGuidanceIntegrationTest {
  private static final IntegrationRequest INSTALL_HOOKS =
      IntegrationRequest.install(Guidance.EXCLUDED);
  private static final IntegrationRequest INSTALL_ALL =
      IntegrationRequest.install(Guidance.INCLUDED);
  private static final IntegrationRequest REMOVE_ALL = IntegrationRequest.remove(Guidance.INCLUDED);
  private static final IntegrationRequest REMOVE_GUIDANCE =
      IntegrationRequest.remove(Guidance.ONLY);

  @TempDir Path root;

  static Stream<Arguments> agents() {
    return Stream.of(
        Arguments.of(
            new ClaudeCodeIntegration(),
            ClaudeCodeIntegration.GUIDANCE_FILE,
            List.of(".claude/settings.json", ".claude/hooks/sentinel-edit-write"),
            List.of(".claude/skills/openspec-propose/SKILL.md", ".claude/commands/opsx/apply.md")),
        Arguments.of(
            new OpenCodeIntegration(),
            OpenCodeIntegration.GUIDANCE_FILE,
            List.of(
                ".opencode/commands/sentinel-check.md", ".opencode/plugins/sentinel-edit-write.js"),
            List.of(
                ".opencode/skills/openspec-propose/SKILL.md", ".opencode/commands/opsx-apply.md")));
  }

  @ParameterizedTest
  @MethodSource("agents")
  void installsGuidanceOutsideOpenSpecNamespace(
      final AgentIntegration agent,
      final String guidance,
      final List<String> hooks,
      final List<String> openSpec)
      throws IOException {
    final IntegrationResult result = agent.integrate(root, INSTALL_ALL);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(result.changed()).contains(root.resolve(guidance).toString());
    assertThat(root.resolve(guidance)).hasContent(OpenSpecGuidance.content());
    hooks.forEach(hook -> assertThat(root.resolve(hook)).exists());
    final Path skillDirectory = Path.of(guidance).getParent();
    assertThat(skillDirectory.getFileName().toString())
        .doesNotStartWith("openspec")
        .doesNotStartWith("opsx");
    assertThat(agent.integrate(root, INSTALL_ALL).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
  }

  @ParameterizedTest
  @MethodSource("agents")
  void refreshesOutdatedOwnedGuidanceOnlyWhenSelected(
      final AgentIntegration agent,
      final String guidance,
      final List<String> hooks,
      final List<String> openSpec)
      throws IOException {
    agent.integrate(root, INSTALL_HOOKS);
    final Path target = root.resolve(guidance);
    Files.createDirectories(target.getParent());
    Files.writeString(target, OpenSpecGuidance.MARKER + "\nold guidance\n");

    assertThat(agent.integrate(root, INSTALL_HOOKS).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(target).hasContent(OpenSpecGuidance.MARKER + "\nold guidance\n");

    final IntegrationResult refreshed = agent.integrate(root, INSTALL_ALL);
    assertThat(refreshed.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(refreshed.changed()).containsExactly(target.toString());
    assertThat(target).hasContent(OpenSpecGuidance.content());
  }

  @ParameterizedTest
  @MethodSource("agents")
  void plainRemovalRemovesHooksAndGuidance(
      final AgentIntegration agent,
      final String guidance,
      final List<String> hooks,
      final List<String> openSpec)
      throws IOException {
    agent.integrate(root, INSTALL_ALL);

    final IntegrationResult removed = agent.integrate(root, REMOVE_ALL);

    assertThat(removed.status()).isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(root.resolve(guidance)).doesNotExist();
    assertThat(root.resolve(guidance).getParent().getParent()).doesNotExist();
    hooks.forEach(hook -> assertThat(root.resolve(hook)).doesNotExist());
  }

  @ParameterizedTest
  @MethodSource("agents")
  void guidanceOnlyRemovalKeepsHooks(
      final AgentIntegration agent,
      final String guidance,
      final List<String> hooks,
      final List<String> openSpec)
      throws IOException {
    agent.integrate(root, INSTALL_ALL);

    final IntegrationResult removed = agent.integrate(root, REMOVE_GUIDANCE);

    assertThat(removed.status()).isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(removed.changed()).containsExactly(root.resolve(guidance).toString());
    assertThat(root.resolve(guidance).getParent()).doesNotExist();
    hooks.forEach(hook -> assertThat(root.resolve(hook)).exists());
    assertThat(agent.integrate(root, REMOVE_GUIDANCE).status())
        .isEqualTo(IntegrationResult.Status.NOT_FOUND);
  }

  @ParameterizedTest
  @MethodSource("agents")
  void userOwnedGuidanceBlocksEveryWriteAndDelete(
      final AgentIntegration agent,
      final String guidance,
      final List<String> hooks,
      final List<String> openSpec)
      throws IOException {
    final Path target = root.resolve(guidance);
    Files.createDirectories(target.getParent());
    Files.writeString(target, "user guidance\n");

    final IntegrationResult install = agent.integrate(root, INSTALL_ALL);
    assertThat(install.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(install.changed()).containsExactly(target.toString());
    hooks.forEach(hook -> assertThat(root.resolve(hook)).doesNotExist());

    agent.integrate(root, INSTALL_HOOKS);
    assertThat(agent.integrate(root, REMOVE_ALL).status())
        .isEqualTo(IntegrationResult.Status.CONFLICT);
    hooks.forEach(hook -> assertThat(root.resolve(hook)).exists());
    assertThat(target).hasContent("user guidance\n");
  }

  @ParameterizedTest
  @MethodSource("agents")
  void leavesOpenSpecOwnedFilesByteForByteUnchanged(
      final AgentIntegration agent,
      final String guidance,
      final List<String> hooks,
      final List<String> openSpec)
      throws IOException {
    for (final String file : openSpec) {
      Files.createDirectories(root.resolve(file).getParent());
      Files.writeString(root.resolve(file), "official " + file + "\n");
    }

    agent.integrate(root, INSTALL_ALL);
    agent.integrate(root, REMOVE_GUIDANCE);
    agent.integrate(root, INSTALL_ALL);
    agent.integrate(root, REMOVE_ALL);

    for (final String file : openSpec) {
      assertThat(root.resolve(file)).hasContent("official " + file + "\n");
    }
  }
}
