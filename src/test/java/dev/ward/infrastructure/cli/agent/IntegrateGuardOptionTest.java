package dev.ward.infrastructure.cli.agent;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.agent.IntegrationService;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.agent.AgentIntegration;
import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class IntegrateGuardOptionTest {
  private static final String AGENT = "claude-code";
  private static final String DIRECTORY = "-C";

  @TempDir Path root;

  private final List<IntegrationRequest> requests = new ArrayList<>();
  private final StringWriter err = new StringWriter();

  @BeforeEach
  void project() {
    withPom(root, PLAIN_POM);
  }

  private int run(final String... args) {
    final AgentIntegration recording =
        new AgentIntegration() {
          @Override
          public List<PlannedFileChange> plan(final Path projectRoot, final GuardMode guard) {
            return List.of();
          }

          @Override
          public String id() {
            return AGENT;
          }

          @Override
          public IntegrationResult integrate(final Path projectRoot, final boolean remove) {
            return integrate(projectRoot, new IntegrationRequest(remove, GuardMode.KEEP));
          }

          @Override
          public IntegrationResult integrate(
              final Path projectRoot, final IntegrationRequest request) {
            requests.add(request);
            return new IntegrationResult(IntegrationResult.Status.CHANGED, List.of(), "ok");
          }
        };
    final IntegrateCommand command =
        new IntegrateCommand(
            new IntegrationService(
                new ProjectDetector(new FileSystemProjectInspection()), List.of(recording)),
            new ByteArrayInputStream(new byte[0]));
    final CommandLine cli = new CommandLine(command);
    cli.setOut(new PrintWriter(new StringWriter(), true));
    cli.setErr(new PrintWriter(err, true));
    return cli.execute(args);
  }

  @Test
  void omittedOptionKeepsTheGuardState() {
    assertThat(run(AGENT, DIRECTORY, root.toString())).isEqualTo(ExitCodes.OK);

    assertThat(requests).containsExactly(IntegrationRequest.INSTALL);
  }

  @Test
  void optionEnablesAndNegatedOptionDisables() {
    assertThat(run(AGENT, "--guard-approved-specs", DIRECTORY, root.toString()))
        .isEqualTo(ExitCodes.OK);
    assertThat(run(AGENT, "--no-guard-approved-specs", DIRECTORY, root.toString()))
        .isEqualTo(ExitCodes.OK);

    assertThat(requests)
        .containsExactly(
            new IntegrationRequest(false, GuardMode.ENABLE),
            new IntegrationRequest(false, GuardMode.DISABLE));
  }

  @Test
  void removeCannotBeCombinedWithAGuardOption() {
    assertThat(run(AGENT, "--remove", "--guard-approved-specs", DIRECTORY, root.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(run(AGENT, "--remove", "--no-guard-approved-specs", DIRECTORY, root.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(run(AGENT, "--remove", DIRECTORY, root.toString())).isEqualTo(ExitCodes.OK);

    assertThat(requests).containsExactly(IntegrationRequest.REMOVE);
    assertThat(err.toString()).contains("--remove cannot be combined");
  }
}
