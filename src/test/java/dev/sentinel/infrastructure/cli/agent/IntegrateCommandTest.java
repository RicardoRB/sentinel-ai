package dev.sentinel.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.application.agent.IntegrationService;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationRequest.Guidance;
import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.agent.OpenSpecSetup;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

class IntegrateCommandTest {
  @TempDir Path project;
  private final StringWriter out = new StringWriter();
  private final List<IntegrationRequest> requests = new ArrayList<>();
  private IntegrationResult nextResult =
      new IntegrationResult(IntegrationResult.Status.CHANGED, List.of("file"), "done");

  @BeforeEach
  void createProject() throws IOException {
    Files.writeString(project.resolve("pom.xml"), "<project/>\n");
  }

  @ParameterizedTest
  @ValueSource(strings = {"opencode", "claude-code"})
  void mapsFlagCombinationsToRequests(final String agent) {
    assertThat(run(agent)).isEqualTo(ExitCodes.OK);
    assertThat(run(agent, "--openspec-guidance")).isEqualTo(ExitCodes.OK);
    assertThat(run(agent, "--remove")).isEqualTo(ExitCodes.OK);
    assertThat(run(agent, "--openspec-guidance", "--remove")).isEqualTo(ExitCodes.OK);

    assertThat(requests)
        .containsExactly(
            IntegrationRequest.install(Guidance.EXCLUDED),
            IntegrationRequest.install(Guidance.INCLUDED),
            IntegrationRequest.remove(Guidance.INCLUDED),
            IntegrationRequest.remove(Guidance.ONLY));
  }

  @ParameterizedTest
  @ValueSource(strings = {"opencode", "claude-code"})
  void outputWithoutGuidanceFlagIsUnchanged(final String agent) {
    run(agent);

    assertThat(out.toString())
        .isEqualTo("CHANGED: done" + System.lineSeparator() + "file" + System.lineSeparator());
  }

  @ParameterizedTest
  @ValueSource(strings = {"opencode", "claude-code"})
  void printsWarningsAfterChangedPathsAndSucceeds(final String agent) {
    nextResult = nextResult.withWarning("enable verify");

    assertThat(run(agent, "--openspec-guidance")).isEqualTo(ExitCodes.OK);

    assertThat(out.toString())
        .endsWith(
            "file" + System.lineSeparator() + "WARNING: enable verify" + System.lineSeparator());
  }

  @ParameterizedTest
  @ValueSource(strings = {"UNAVAILABLE", "CONFLICT"})
  void failedResultsExitWithError(final String status) {
    nextResult =
        new IntegrationResult(IntegrationResult.Status.valueOf(status), List.of(), "blocked");

    assertThat(run("claude-code", "--openspec-guidance")).isEqualTo(ExitCodes.ERROR);
    assertThat(out.toString()).contains(status + ": blocked");
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "2"})
  void interactivePromptStillSelectsAgent(final String choice) {
    final CommandLine cli =
        commandLine(new ByteArrayInputStream((choice + "\n").getBytes(StandardCharsets.UTF_8)));

    assertThat(cli.execute("--openspec-guidance", "-C", project.toString()))
        .isEqualTo(ExitCodes.OK);
    assertThat(out.toString()).contains("Select an agent integration");
    assertThat(requests).containsExactly(IntegrationRequest.install(Guidance.INCLUDED));
  }

  private int run(final String agent, final String... flags) {
    final List<String> args = new ArrayList<>(List.of(agent));
    args.addAll(List.of(flags));
    args.addAll(List.of("-C", project.toString()));
    return commandLine(new ByteArrayInputStream(new byte[0])).execute(args.toArray(String[]::new));
  }

  private CommandLine commandLine(final ByteArrayInputStream input) {
    final IntegrationService service =
        new IntegrationService(
            new ProjectDetector(new FileSystemProjectInspection()),
            List.of(recording("opencode"), recording("claude-code")),
            (root, layout) -> new OpenSpecSetup(true, true, true));
    final CommandLine cli = new CommandLine(new IntegrateCommand(service, input));
    cli.setOut(new PrintWriter(out, true));
    return cli;
  }

  private AgentIntegration recording(final String id) {
    return new AgentIntegration() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public IntegrationResult integrate(final Path root, final IntegrationRequest request) {
        requests.add(request);
        return nextResult;
      }
    };
  }
}
