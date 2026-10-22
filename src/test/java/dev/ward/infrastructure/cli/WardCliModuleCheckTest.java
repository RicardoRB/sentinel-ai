package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Commands started inside a build module use the aggregator's project and configuration. */
@DisabledOnOs(OS.WINDOWS)
class WardCliModuleCheckTest extends WardCliIntegrationSupport {
  @Test
  void checkStartedInMavenModuleUsesAggregatorConfiguration() throws IOException {
    final Path pom = project.resolve("pom.xml");
    Files.writeString(
        pom,
        Files.readString(pom)
            .replace("</project>", "    <modules><module>core</module></modules>\n</project>"));
    final Path core = Files.createDirectories(project.resolve("core"));
    Files.writeString(core.resolve("pom.xml"), "<project><artifactId>core</artifactId></project>");
    run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, "format", "-C", project.toString());
    out.getBuffer().setLength(0);
    err.getBuffer().setLength(0);

    assertThat(run(CHECK, FORMAT_OPTION, JSON, "-C", core.toString())).isEqualTo(ExitCodes.OK);
    assertThat(out.toString()).contains("\"format\"");
    assertThat(core.resolve(WARD_TOML)).doesNotExist();
  }
}
