package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.WINDOWS)
class WardCliExcludeSpecTest extends WardCliSpecSupport {
  @Test
  void checkExcludeSpecSkipsSpecGatesAndKeepsJsonOnlyStdout() throws IOException {
    configure(active() + "[quality-gates.tests]\ncommand = \"./mvnw -q test\"\n");

    assertThat(run(CHECK, FORMAT_OPTION, JSON, "--exclude-spec", DIRECTORY, project.toString()))
        .isEqualTo(ExitCodes.OK);
    final JsonTree report = JsonTree.parse(out.toString());
    assertThat(out.toString()).doesNotContain("openspec");
    assertThat(report.get(STATUS).asString()).isEqualTo("PASSED");
    assertThat(project.resolve("calls.log")).doesNotExist();
  }

  @Test
  void checkExcludeSpecWithOnlySpecGatesPasses() throws IOException {
    configure(active());

    assertThat(run(CHECK, "--exclude-spec", DIRECTORY, project.toString())).isEqualTo(ExitCodes.OK);
    assertThat(project.resolve("calls.log")).doesNotExist();
  }

  @Test
  void checkWithoutExcludeSpecStillRunsSpecGates() throws IOException {
    configure(active());

    assertThat(run(CHECK, DIRECTORY, project.toString())).isEqualTo(ExitCodes.OK);
    assertThat(calls()).isEqualTo("validate --all --strict --no-interactive\n");
  }
}
