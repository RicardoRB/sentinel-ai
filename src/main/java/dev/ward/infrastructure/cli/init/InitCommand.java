package dev.ward.infrastructure.cli.init;

import dev.ward.application.init.InitService;
import dev.ward.application.init.InitSetupCatalog;
import dev.ward.domain.init.RawTerminal;
import dev.ward.domain.init.RawTerminal.RawSession;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.cli.ProjectOptions;
import dev.ward.infrastructure.cli.VersionProvider;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** The {@code ward init} command: sets up Ward through options or the setup wizard. */
@Command(
    name = "init",
    description = "Create ward.toml with an interactive setup wizard.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class InitCommand implements Callable<Integer> {
  private static final RawTerminal UNAVAILABLE_TERMINAL = Optional::empty;

  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Mixin private final ProjectOptions options = new ProjectOptions();

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Mixin private final GeneratedFileOptions generatedFiles = new GeneratedFileOptions();

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Option(
      names = "--integration",
      split = ",",
      description =
          "Agent integration(s): none, opencode, or claude-code. Repeat or comma-separate.")
  private List<String> integrations;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Option(
      names = "--gate",
      split = ",",
      description = "Quality gate(s) to enable. Repeat or comma-separate.")
  private List<String> gates;

  @Option(
      names = "--tool",
      split = ",",
      description = "Optional project tool(s): openspec. Repeat or comma-separate.")
  private List<String> tools;

  @Option(
      names = "--openspec-validation",
      split = ",",
      description = "OpenSpec validation(s): openspec, openspec-archived.")
  private List<String> openSpecValidations;

  @Option(
      names = "--install-tool",
      split = ",",
      description = "Explicitly consent to install a selected project tool or gate executable.")
  private List<String> installTools;

  @Option(
      names = "--architecture",
      description =
          "Architecture style for architecture/ArchUnit tests: layered, hexagonal, or clean.")
  private String architecture;

  @Option(names = "--preset", description = "Quality preset: standard or strict.")
  private String preset;

  @Option(
      names = "--quality-hooks",
      description = "Install Ward pre-commit and pre-push quality hooks.")
  private Boolean qualityHooks;

  @Option(
      names = "--remove-quality-hooks",
      description = "Remove Ward-managed quality-hook sections without changing user content.")
  private boolean removeQualityHooks;

  @Option(
      names = "--project",
      paramLabel = "<dir>",
      description =
          "Maven or Gradle project to configure, relative to -C. Repeat to configure several.")
  private List<String> projects;

  @Option(names = "--overwrite", description = "Overwrite an existing ward.toml without prompting.")
  private boolean overwriteOption;

  @Option(
      names = "--dry-run",
      description = "Show the files init would change, then exit without changing anything.")
  private boolean dryRun;

  @Option(
      names = "--accept-compatibility-updates",
      description = "Approve validated Maven compatibility updates without prompting.")
  private boolean acceptCompatibilityUpdates;

  @Option(
      names = "--yes",
      description =
          "Accept defaults for unanswered questions, skip the review, and approve validated"
              + " compatibility updates.")
  private boolean yesOption;

  private final Supplier<InitService> service;
  private final LinePrompt prompt;
  private final RawTerminal rawTerminal;
  private final boolean terminal;

  /** Creates the command reading answers from standard input without raw-key menus. */
  public InitCommand(final InitService service) {
    this(service, System.in, UNAVAILABLE_TERMINAL);
  }

  /** Creates the command reading answers from {@code input} without raw-key menus. */
  public InitCommand(final InitService service, final InputStream input) {
    this(service, input, UNAVAILABLE_TERMINAL);
  }

  /** Creates the command reading standard input, with raw-key menus from {@code rawTerminal}. */
  @Inject
  public InitCommand(final InitService service, final RawTerminal rawTerminal) {
    this(service, System.in, rawTerminal);
  }

  /**
   * Creates the command reading answers from {@code input}, treating it as a terminal only when it
   * is the console.
   */
  public InitCommand(
      final InitService service, final InputStream input, final RawTerminal rawTerminal) {
    this(service, input, rawTerminal, input.equals(System.in) && consoleIsTerminal());
  }

  /** Lets tests decide whether input behaves like an interactive terminal. */
  InitCommand(
      final InitService service,
      final InputStream input,
      final RawTerminal rawTerminal,
      final boolean terminal) {
    this.service = () -> service;
    this.prompt =
        new LinePrompt(new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)));
    this.rawTerminal = rawTerminal;
    this.terminal = terminal;
  }

  private static boolean consoleIsTerminal() {
    return System.console() != null && System.console().isTerminal();
  }

  @Override
  public Integer call() {
    final InitService init = service.get();
    final InitWizard wizard = new InitWizard(prompt, this::openRawTerminal, output(), terminal);
    final List<Project> chosen =
        InitProjectChoice.choose(
            init.targets(options.directory(), projects == null ? List.of() : projects),
            options.directory(),
            terminal && !yesOption,
            wizard,
            output());
    final InitSetupCatalog catalog = init.catalog();
    if (removeQualityHooks) {
      chosen.forEach(project -> output().println(catalog.removeQualityHooks(project).message()));
      return ExitCodes.OK;
    }
    final InitConfirmations confirmations = new InitConfirmations(prompt, output(), terminal);
    final List<InitApply.Target> targets =
        new InitTargetAnswers(
                answers(),
                new InitPrompts(wizard, confirmations, new QuestionProgress(output())),
                terminal,
                overwriteOption,
                output())
            .resolve(init, chosen);
    final InitApply apply = new InitApply(init, confirmations, output());
    if (targets.isEmpty()) {
      return ExitCodes.OK;
    }
    final InitApply.Target first = targets.getFirst();
    return targets.equals(List.of(first))
        ? apply.run(first.project(), first.selection(), mode(first.overwrite()))
        : apply.runAll(targets, mode(false), options.directory().toAbsolutePath().normalize());
  }

  private InitApply.Mode mode(final boolean overwrite) {
    return new InitApply.Mode(
        dryRun, terminal && !yesOption, yesOption || acceptCompatibilityUpdates, overwrite);
  }

  private InitQuestions.Answers answers() {
    return new InitQuestions.Answers(
        preset,
        integrations,
        gates,
        architecture,
        tools,
        openSpecValidations,
        installTools,
        qualityHooks,
        generatedFiles.ci(),
        generatedFiles.agentRules(),
        yesOption);
  }

  private Optional<RawSession> openRawTerminal() {
    if (!terminal || System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
      return Optional.empty();
    }
    return rawTerminal.open();
  }

  private PrintWriter output() {
    return spec == null
        ? new PrintWriter(System.out, true, StandardCharsets.UTF_8)
        : spec.commandLine().getOut();
  }
}
