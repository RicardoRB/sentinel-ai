package dev.sentinel.config;

import static dev.sentinel.ProjectSnapshot.assertPlanMatches;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.ProjectSnapshot;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.GuardMode;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.PlannedFileChange;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Every registered integration's plan matches what installing it writes, in every guard mode. */
class IntegrationPlanContractTest {
  @TempDir Path root;

  static Stream<Arguments> integrations() {
    return SentinelProvidesModule.agentIntegrations().stream()
        .sorted(Comparator.comparing(AgentIntegration::id))
        .flatMap(
            integration ->
                Arrays.stream(GuardMode.values())
                    .map(guard -> Arguments.of(integration.id(), integration, guard)));
  }

  @ParameterizedTest(name = "{0} {2} on an empty project")
  @MethodSource("integrations")
  void installMatchesPlan(
      final String id, final AgentIntegration integration, final GuardMode guard) {
    assertPlanMatches(root, () -> integration.plan(root, guard), () -> install(integration, guard));
  }

  @ParameterizedTest(name = "{0} {2} after an install with the guard enabled")
  @MethodSource("integrations")
  void refreshMatchesPlan(
      final String id, final AgentIntegration integration, final GuardMode guard) {
    install(integration, GuardMode.ENABLE);

    assertPlanMatches(root, () -> integration.plan(root, guard), () -> install(integration, guard));
  }

  @ParameterizedTest(name = "{0} {2} with one installed file missing")
  @MethodSource("integrations")
  void partialInstallMatchesPlan(
      final String id, final AgentIntegration integration, final GuardMode guard)
      throws IOException {
    final Map<String, String> before = ProjectSnapshot.of(root);
    install(integration, GuardMode.KEEP);
    final String created =
        ProjectSnapshot.of(root).keySet().stream()
            .filter(file -> !before.containsKey(file))
            .findFirst()
            .orElseThrow();
    Files.delete(root.resolve(created));

    assertPlanMatches(root, () -> integration.plan(root, guard), () -> install(integration, guard));
  }

  @ParameterizedTest(name = "{0} {2} with a user-owned file in the way")
  @MethodSource("integrations")
  void conflictIsPlannedAndNothingIsWritten(
      final String id, final AgentIntegration integration, final GuardMode guard)
      throws IOException {
    final Path target = integration.plan(root, GuardMode.KEEP).getFirst().file();
    Files.createDirectories(target.getParent());
    Files.writeString(target, "user-owned\n");

    final List<PlannedFileChange> plan = integration.plan(root, guard);
    final Map<String, String> before = ProjectSnapshot.of(root);
    install(integration, guard);

    assertThat(plan)
        .extracting(PlannedFileChange::action)
        .contains(PlannedFileChange.Action.CONFLICT);
    assertThat(ProjectSnapshot.of(root)).isEqualTo(before);
  }

  private void install(final AgentIntegration integration, final GuardMode guard) {
    integration.integrate(root, new IntegrationRequest(false, guard));
  }
}
