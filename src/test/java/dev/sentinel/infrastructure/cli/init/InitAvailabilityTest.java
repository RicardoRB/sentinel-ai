package dev.sentinel.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.application.init.InitSetupCatalog;
import dev.sentinel.domain.FakeEnvironmentInspection;
import dev.sentinel.domain.init.RawTerminal.RawSession;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import dev.sentinel.infrastructure.cli.ExitCodes;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unavailable gates are marked before selection and reported with their remediation. */
class InitAvailabilityTest {
  @TempDir Path root;

  @Test
  void numberedGateListMarksUnavailableGates() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    init.run("3\n1\n1\n");

    assertThat(init.output()).contains("semgrep - ", "[unavailable]").doesNotContain("[available]");
  }

  @Test
  void rawGateMenuMarksUnavailableGates() {
    final StringWriter output = new StringWriter();
    final InitWizard wizard =
        new InitWizard(
            new LinePrompt(new BufferedReader(new StringReader(""))),
            () -> Optional.of(new ScriptedSession(' ', '\n')),
            new PrintWriter(output, true),
            true);

    final List<String> selected =
        wizard.selectGates(
            new InitSetupCatalog(new FakeEnvironmentInspection()),
            new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE),
            List.of());

    assertThat(selected).containsExactly("tests");
    assertThat(output.toString()).contains("semgrep - ").contains("[unavailable]");
  }

  @Test
  void selectedUnavailableGateShowsItsRemediation() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);
    final String remediation =
        new InitSetupCatalog(new FakeEnvironmentInspection())
            .gate(new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE), "semgrep")
            .availabilityMessage();

    final int exit = init.run("", "--integration", "none", "--gate", "semgrep");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output())
        .contains("Selected quality gate 'semgrep': UNAVAILABLE - " + remediation);
  }

  /** Replays the given keys, then reports end of input. */
  private static final class ScriptedSession implements RawSession {
    private final Iterator<Integer> keys;

    ScriptedSession(final int... keys) {
      this.keys = Arrays.stream(keys).iterator();
    }

    @Override
    public int read() {
      return keys.hasNext() ? keys.next() : -1;
    }

    @Override
    public void close() {
      // Nothing to restore for a scripted session.
    }
  }
}
