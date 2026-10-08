package dev.sentinel.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.TestProjects;
import dev.sentinel.application.gate.CheckService;
import dev.sentinel.application.gate.CommandQualityGate;
import dev.sentinel.application.gate.LanguageGateRegistry;
import dev.sentinel.application.gate.MavenTestGate;
import dev.sentinel.application.gate.QualityGateFactory;
import dev.sentinel.application.gate.QualityGateRunner;
import dev.sentinel.application.init.InitSetupCatalog;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.project.ProjectDiscovery;
import dev.sentinel.config.DaggerSentinelComponent;
import dev.sentinel.domain.FakeEnvironmentInspection;
import dev.sentinel.domain.agent.AgentRequest;
import dev.sentinel.domain.agent.AgentResult;
import dev.sentinel.domain.config.CommandLineTokenizer;
import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.gate.SkippedQualityGate;
import dev.sentinel.domain.init.ArchitectureTestChange;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitResult;
import dev.sentinel.domain.init.PomChange;
import dev.sentinel.domain.loop.LoopConfiguration;
import dev.sentinel.domain.policy.PolicyEvaluator;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.process.CommandResult;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import dev.sentinel.infrastructure.agent.ProcessAgentRunner;
import dev.sentinel.infrastructure.cli.CommandLineRunnerImpl;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.VersionProvider;
import dev.sentinel.infrastructure.cli.gate.JsonReportRenderer;
import dev.sentinel.infrastructure.cli.gate.TextReportRenderer;
import dev.sentinel.infrastructure.config.TomlConfigurationReader;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.PomToolConfigurator;
import dev.sentinel.infrastructure.json.ForyJsonCodec;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoverageExpansionTest {
  private static final Project PROJECT =
      new Project(Path.of("/project"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  @Test
  void discoversEverySupportedProjectMarker(final @TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("maven"));
    Files.writeString(root.resolve("maven/pom.xml"), TestProjects.PLAIN_POM);
    Files.createDirectories(root.resolve("gradle"));
    Files.writeString(root.resolve("gradle/build.gradle"), "");
    Files.createDirectories(root.resolve("kotlin"));
    Files.writeString(root.resolve("kotlin/build.gradle.kts"), "");
    Files.createDirectories(root.resolve("typescript"));
    Files.writeString(root.resolve("typescript/tsconfig.json"), "{}");
    Files.createDirectories(root.resolve("javascript"));
    Files.writeString(root.resolve("javascript/package.json"), "{}");
    Files.writeString(root.resolve("javascript/yarn.lock"), "");
    Files.createDirectories(root.resolve("python-poetry"));
    Files.writeString(root.resolve("python-poetry/pyproject.toml"), "");
    Files.createDirectories(root.resolve("python-pip"));
    Files.writeString(root.resolve("python-pip/requirements.txt"), "");
    Files.createDirectories(root.resolve("go"));
    Files.writeString(root.resolve("go/go.mod"), "module example");
    Files.createDirectories(root.resolve("rust"));
    Files.writeString(root.resolve("rust/Cargo.toml"), "[package]");
    Files.createDirectories(root.resolve("csharp"));
    Files.writeString(root.resolve("csharp/app.csproj"), "<Project/>");

    assertThat(new ProjectDiscovery(new FileSystemProjectInspection()).discover(root))
        .extracting(Project::language)
        .containsExactlyInAnyOrder(
            Language.JAVA,
            Language.JAVA,
            Language.KOTLIN,
            Language.TYPESCRIPT,
            Language.JAVASCRIPT,
            Language.PYTHON,
            Language.PYTHON,
            Language.GO,
            Language.RUST,
            Language.CSHARP);
    assertThat(
            new ProjectDiscovery(new FileSystemProjectInspection()).discover(root.resolve("maven")))
        .hasSize(1);
  }

  @Test
  void projectDiscoverySelectsPackageManagerMarkers(final @TempDir Path root) throws IOException {
    Files.writeString(root.resolve("package.json"), "{}");
    Files.writeString(root.resolve("pnpm-lock.yaml"), "");
    assertThat(
            new ProjectDiscovery(new FileSystemProjectInspection())
                .discover(root)
                .getFirst()
                .buildTool())
        .isEqualTo(BuildTool.PNPM);
    Files.delete(root.resolve("pnpm-lock.yaml"));
    Files.writeString(root.resolve("yarn.lock"), "");
    assertThat(
            new ProjectDiscovery(new FileSystemProjectInspection())
                .discover(root)
                .getFirst()
                .buildTool())
        .isEqualTo(BuildTool.YARN);
  }

  @Test
  void architectureGeneratorCreatesAndRollsBackAllStyles(final @TempDir Path root)
      throws IOException {
    Files.createDirectories(root.resolve("src/main/java/com/acme"));
    Files.writeString(
        root.resolve("src/main/java/com/acme/App.java"), "package com.acme; class App {}");
    final ArchitectureTestGenerator generator = new ArchitectureTestGenerator();
    for (final String style : List.of("layered", "hexagonal", "clean")) {
      final ArchitectureTestChange change =
          generator.apply(new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE), style);
      assertThat(change.created()).isTrue();
      assertThat(Files.readString(change.file())).contains("package com.acme;", style);
      generator.rollback(change);
    }
    final Project project = new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
    final ArchitectureTestChange existing = generator.apply(project, "layered");
    assertThat(generator.apply(project, "clean").created()).isFalse();
    generator.rollback(existing);
  }

  @Test
  void architectureGeneratorDefaultsPackageAndPreservesExisting(final @TempDir Path root)
      throws IOException {
    final ArchitectureTestGenerator generator = new ArchitectureTestGenerator();
    final ArchitectureTestChange change =
        generator.apply(
            new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE), "layered");
    assertThat(change.file())
        .isEqualTo(root.resolve("src/test/java/com/example/ArchitectureTest.java"));
    assertThat(Files.readString(change.file())).contains("package com.example;");
    generator.rollback(change);
    Files.createDirectories(change.file().getParent());
    Files.writeString(change.file(), "user test");
    assertThat(
            generator
                .apply(new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE), "clean")
                .created())
        .isFalse();
    generator.rollback(null);
  }

  @Test
  void pomConfiguratorAddsAndRollsBackAllTools(final @TempDir Path root) throws IOException {
    TestProjects.withPom(root, TestProjects.PLAIN_POM);
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());
    final List<InitGateOption> gates =
        catalog.gates(new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE));
    final PomToolConfigurator configurator = new PomToolConfigurator();
    final PomChange change =
        configurator.apply(
            new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE), gates);
    assertThat(change.tools())
        .containsExactly(
            "maven-checkstyle-plugin",
            "spotbugs-maven-plugin",
            "sonar-maven-plugin",
            "jacoco-maven-plugin",
            "dependency-check-maven",
            "archunit-junit5",
            "pitest-maven",
            "maven-enforcer-plugin",
            "spotless-maven-plugin",
            "license-maven-plugin",
            "japicmp-maven-plugin");
    assertThat(Files.readString(root.resolve("pom.xml")))
        .contains(
            "jacoco-maven-plugin",
            "maven-checkstyle-plugin",
            "spotbugs-maven-plugin",
            "sonar-maven-plugin",
            "dependency-check-maven",
            "archunit-junit5",
            "pitest-maven",
            "maven-enforcer-plugin");
    configurator.rollback(change);
    assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(TestProjects.PLAIN_POM);
    configurator.rollback(null);
    assertThat(new PomChange(root.resolve("pom.xml"), "", List.of()).changed()).isFalse();
  }

  @Test
  void pomConfiguratorHandlesExistingBuildSections(final @TempDir Path root) throws IOException {
    final String pom =
        "<project><build><plugins></plugins></build><dependencies></dependencies></project>";
    Files.writeString(root.resolve("pom.xml"), pom);
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());
    final Project project = new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
    final PomChange change =
        new PomToolConfigurator()
            .apply(
                project,
                List.of(catalog.gate(project, "checkstyle"), catalog.gate(project, "archunit")));
    assertThat(change.changed()).isTrue();
    assertThat(Files.readString(root.resolve("pom.xml"))).contains("<plugins>", "<dependencies>");
  }

  @Test
  void gateDomainCoversPassFailureSkippedAndExecutionError() {
    final CommandExecutor executor =
        (CommandExecutor)
            (command, root) ->
                new CommandResult(
                    "fail".equals(command.getFirst()) ? 1 : 0, "out", "err", Duration.ofMillis(2));
    assertThat(new CommandQualityGate("custom", executor, List.of("ok")).execute(PROJECT).status())
        .isEqualTo(GateStatus.PASSED);
    assertThat(
            new CommandQualityGate("custom", executor, List.of("fail")).execute(PROJECT).status())
        .isEqualTo(GateStatus.FAILED);
    assertThat(new MavenTestGate(executor, List.of("test")).name()).isEqualTo("tests");
    assertThat(new SkippedQualityGate("skip").execute(PROJECT).status())
        .isEqualTo(GateStatus.SKIPPED);
    assertThat(
            new GateResult("x", GateStatus.PASSED, List.of("x"), 0, Duration.ZERO, "", "").passed())
        .isTrue();
  }

  @Test
  void configurationAndTokenizerCoverProfilesAndErrors(final @TempDir Path root)
      throws IOException {
    assertThat(CommandLineTokenizer.tokenize("java -Dname='hello world' app"))
        .containsExactly("java", "-Dname=hello world", "app");
    assertThatThrownBy(() -> CommandLineTokenizer.tokenize("'unterminated"))
        .isInstanceOf(SentinelException.class);
    final Path file = root.resolve("sentinel.toml");
    Files.writeString(
        file,
        """
                version = 1
                [quality-gates.tests]
                enabled = false
                profiles = ["fast"]
                [quality-gates.compile]
                command = ["mvn", "compile"]
                profiles = ["fast"]
                """);
    final SentinelConfiguration configuration = new TomlConfigurationReader().read(file);
    assertThat(configuration.gates()).containsOnlyKeys("tests", "compile");
    assertThat(configuration.profileNames()).containsExactly("fast");
    assertThat(configuration.enabledGates()).containsOnlyKeys("compile");
    assertThatThrownBy(() -> new TomlConfigurationReader().read(root.resolve("missing.toml")))
        .isInstanceOf(SentinelException.class);
  }

  @Test
  void reportAndPolicyModelsCoverStrictAndNonStrictResults() {
    final GateResult passed =
        new GateResult("tests", GateStatus.PASSED, List.of("test"), 0, Duration.ZERO, "ok", "");
    final GateResult failed =
        new GateResult(
            "checkstyle", GateStatus.FAILED, List.of("check"), 1, Duration.ZERO, "", "bad");
    final CheckReport report = new CheckReport(PROJECT, List.of(passed, failed));
    assertThat(report.passed()).isFalse();
    assertThat(report.status()).isEqualTo(GateStatus.FAILED);
    assertThat(new PolicyEvaluator().evaluate(report, false)).isNotEmpty();
    assertThat(new PolicyEvaluator().evaluate(report, true)).isNotEmpty();
    assertThat(new CheckReport(PROJECT, List.of(passed)).passed()).isTrue();
  }

  @Test
  void validatesConfigurationAndValueObjects() {
    assertThatThrownBy(() -> new LoopConfiguration(0, 1, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LoopConfiguration(1, 0, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new LoopConfiguration(2, 3, true).allowDirty()).isTrue();
    assertThat(new GateConfiguration(true, List.of("test")).command()).containsExactly("test");
    assertThat(new SentinelConfiguration(1, Map.of()).enabledGates()).isEmpty();
    assertThat(new AgentResult(true, "done", "").succeeded()).isTrue();
    assertThat(
            new InitResult(Path.of("/tmp/sentinel.toml"), true, false, null, null, null, null)
                .gates())
        .isEmpty();
  }

  @Test
  void rendersTextAndJsonReportsIncludingFailureOutput() {
    final GateResult passed =
        new GateResult(
            "tests", GateStatus.PASSED, List.of("test"), 0, Duration.ofMillis(1500), "ok", "");
    final GateResult failed =
        new GateResult(
            "lint",
            GateStatus.FAILED,
            List.of("lint"),
            1,
            Duration.ofMillis(1),
            "line 1\nline 2",
            "stderr");
    final CheckReport report = new CheckReport(PROJECT, List.of(passed, failed));
    assertThat(new TextReportRenderer().render(report))
        .contains("Sentinel", "Quality Gate: FAILED", "Command:", "line 1");
    assertThat(new JsonReportRenderer(new ForyJsonCodec()).render(report))
        .contains("schemaVersion", "FAILED", "lint", "stderr");
    assertThat(new JsonReportRenderer(new ForyJsonCodec()).renderError("broken"))
        .contains("ERROR", "broken");
  }

  @Test
  void processAgentRunnerAddsTaskAndReportsSuccessOrFailure() {
    final CommandExecutor executor =
        (CommandExecutor)
            (command, root) ->
                new CommandResult(command.contains("fail") ? 1 : 0, "out", "err", Duration.ZERO);
    final ProcessAgentRunner runner =
        new ProcessAgentRunner(executor, Path.of("/tmp"), List.of("agent"));
    assertThat(runner.id()).isEqualTo("external");
    assertThat(runner.run(new AgentRequest("fix", 2)).succeeded()).isTrue();
    final ProcessAgentRunner failing =
        new ProcessAgentRunner(executor, Path.of("/tmp"), List.of("fail"));
    assertThat(failing.run(new AgentRequest("fix", 2)).summary()).isEqualTo("Agent failed.");
  }

  @Test
  void languageRegistryHasDefaultsForEveryLanguage() {
    final LanguageGateRegistry registry = new LanguageGateRegistry();
    for (final Language language : Language.values()) {
      assertThat(registry.defaults(language)).isNotEmpty();
    }
  }

  @Test
  void checkServiceSelectsProfilesAndRejectsInvalidOnes(final @TempDir Path root)
      throws IOException {
    TestProjects.withPom(root, TestProjects.PLAIN_POM);
    Files.writeString(
        root.resolve("sentinel.toml"),
        """
                version = 1
                 [quality-gates.tests]
                 command = "test"
                 profiles = ["tests-only"]
                 [quality-gates.compile]
                 command = "compile"
                """);
    final CommandExecutor executor =
        (CommandExecutor) (command, path) -> new CommandResult(0, "ok", "", Duration.ZERO);
    final CheckService service =
        new CheckService(
            new ProjectDetector(new FileSystemProjectInspection()),
            new TomlConfigurationReader(),
            new QualityGateFactory(executor),
            new QualityGateRunner());
    assertThat(service.check(root, List.of("tests-only")).results()).hasSize(1);
    assertThatThrownBy(() -> service.check(root, List.of("missing")))
        .isInstanceOf(SentinelException.class);
    Files.writeString(
        root.resolve("sentinel.toml"),
        "version = 1\n[quality-gates.tests]\ncommand='test'\nprofiles=['bad']\n");
    assertThatThrownBy(() -> service.check(root))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("--profile");
  }

  @Test
  void commandLineRunnerWiresCommandsAndHandlesKnownAndUnexpectedFailures(final @TempDir Path root)
      throws IOException {
    TestProjects.withPom(root, TestProjects.PLAIN_POM);
    final CommandLineRunnerImpl runner = DaggerSentinelComponent.create().commandLineRunner();
    assertThat(runner.run()).isEqualTo(ExitCodes.ERROR);
    assertThat(runner.run("--help")).isZero();
    assertThat(runner.run("detect", "-C", root.toString())).isZero();
    Files.writeString(
        root.resolve("sentinel.toml"),
        "version = 1\n\n[quality-gates.tests]\nenabled = true\ncommand = \"echo ok\"\n");
    assertThat(runner.run("check", "-C", root.toString())).isZero();
    assertThat(runner.run("unknown-command")).isEqualTo(2);
  }

  @Test
  void versionProviderSupportsRuntimeAndInjectedVersions() {
    assertThat(new VersionProvider().getVersion()).containsExactly("sentinel dev");
    assertThat(new VersionProvider("1.2.3").getVersion()).containsExactly("sentinel 1.2.3");
  }

  @Test
  void validatesProcessAndAgentResults() {
    final CommandResult success = new CommandResult(0, "out", "err", Duration.ZERO);
    assertThat(success.succeeded()).isTrue();
    assertThat(success.hasExecutionError()).isFalse();
    assertThat(new CommandResult(1, "", "", Duration.ZERO, "failed").hasExecutionError()).isTrue();
    assertThatThrownBy(() -> new AgentRequest("", 1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AgentRequest("task", 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
