package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.init.GradleToolConfigurator;
import dev.ward.infrastructure.init.JvmBuildToolConfigurator;
import dev.ward.infrastructure.init.PomToolConfigurator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Until Ward has Kotlin tools, a Kotlin project gets exactly the Java preset gates. */
class InitKotlinPresetTest {
  @TempDir Path java;
  @TempDir Path kotlin;

  @Test
  void kotlinProjectGetsTheJavaStrictGateSet() throws IOException {
    final String javaToml = wardToml(java, "src/main/java");

    assertThat(javaToml).contains("[quality-gates.checkstyle]", "[quality-gates.mutation]");
    assertThat(wardToml(kotlin, "src/main/kotlin")).isEqualTo(javaToml);
  }

  private static String wardToml(final Path root, final String sources) throws IOException {
    Files.writeString(root.resolve("build.gradle.kts"), "plugins { java }\n");
    Files.createDirectories(root.resolve(sources));
    final InitCommandFixture init =
        InitCommandFixture.repository(
            root,
            new JvmBuildToolConfigurator(new PomToolConfigurator(), new GradleToolConfigurator()));

    final int exit =
        init.run(
            "",
            "--preset",
            "strict",
            "--integration",
            "none",
            "--architecture",
            "layered",
            "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    return Files.readString(root.resolve("ward.toml"));
  }
}
