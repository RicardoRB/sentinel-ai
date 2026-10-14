package dev.sentinel.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.FakeCommandExecutor;
import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MavenTestGateTest {

  private final Project project =
      new Project(Path.of("/work/app"), Language.JAVA, BuildTool.MAVEN, Framework.SPRING_BOOT);

  @Test
  void passesWhenCommandExitsZero() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "BUILD SUCCESS", "");
    final MavenTestGate gate = new MavenTestGate(executor, List.of("./mvnw", "test"));

    final GateResult result = gate.execute(project);

    assertThat(gate.name()).isEqualTo("tests");
    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout()).isEqualTo("BUILD SUCCESS");
    assertThat(result.duration()).isEqualTo(Duration.ofMillis(1234));
    assertThat(executor.commands).containsExactly(List.of("./mvnw", "test"));
    assertThat(executor.workingDirectories).containsExactly(Path.of("/work/app"));
  }

  @Test
  void failsWhenCommandExitsNonZero() {
    final MavenTestGate gate =
        new MavenTestGate(new FakeCommandExecutor(1, "out", "boom"), List.of("./mvnw", "test"));

    final GateResult result = gate.execute(project);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr()).isEqualTo("boom");
    assertThat(result.command()).containsExactly("./mvnw", "test");
  }

  @Test
  void fallsBackToSystemGradleWhenWrapperIsUnavailable() {
    final Project gradleProject =
        new Project(Path.of("/work/app"), Language.JAVA, BuildTool.GRADLE, Framework.NONE, false);
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "BUILD SUCCESS", "");
    final QualityGateFactory factory = new QualityGateFactory(executor);
    final SentinelConfiguration configuration =
        new SentinelConfiguration(
            1, Map.of("tests", new GateConfiguration(true, List.of("./gradlew", "test"))));

    factory.create(configuration, gradleProject).getFirst().execute(gradleProject);

    assertThat(executor.commands).containsExactly(List.of("gradle", "test"));
  }
}
