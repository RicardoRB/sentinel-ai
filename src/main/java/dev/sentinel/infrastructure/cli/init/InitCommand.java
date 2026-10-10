package dev.sentinel.infrastructure.cli.init;

import dev.sentinel.application.init.InitService;
import dev.sentinel.application.init.InitSetupCatalog;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.init.InitResult;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.init.RawTerminal;
import dev.sentinel.domain.init.RawTerminal.RawSession;
import dev.sentinel.domain.project.Project;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
import dev.sentinel.infrastructure.cli.VersionProvider;
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

@Command(
    name = "init",
    description = "Create sentinel.toml with an interactive setup wizard.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class InitCommand implements Callable<Integer> {
  private static final RawTerminal UNAVAILABLE_TERMINAL = Optional::empty;

  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Mixin private final ProjectOptions options = new ProjectOptions();

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
      names = "--architecture",
      description =
          "Architecture style for architecture/ArchUnit tests: layered, hexagonal, or clean.")
  private String architecture;

  @Option(names = "--preset", description = "Quality preset: standard or strict.")
  private String preset;

  @Option(
      names = "--overwrite",
      description = "Overwrite an existing sentinel.toml without prompting.")
  private boolean overwriteOption;

  private final Supplier<InitService> service;
  private final InputStream input;
  private final LinePrompt prompt;
  private final RawTerminal rawTerminal;

  public InitCommand(final InitService service) {
    this(service, System.in, UNAVAILABLE_TERMINAL);
  }

  public InitCommand(final InitService service, final InputStream input) {
    this(service, input, UNAVAILABLE_TERMINAL);
  }

  @Inject
  public InitCommand(final InitService service, final RawTerminal rawTerminal) {
    this(service, System.in, rawTerminal);
  }

  public InitCommand(
      final InitService service, final InputStream input, final RawTerminal rawTerminal) {
    this.service = () -> service;
    this.input = input;
    this.prompt =
        new LinePrompt(new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)));
    this.rawTerminal = rawTerminal;
  }

  @Override
  public Integer call() {
    final InitService init = service.get();
    final Project project = init.project(options.directory());
    final InitSetupCatalog catalog = init.catalog();
    final QualityPreset requestedPreset = preset == null ? null : catalog.preset(preset);
    final InitWizard wizard = new InitWizard(prompt, this::openRawTerminal, output());
    final boolean exists = init.configurationExists(project);
    if (exists && !overwriteOption && !overwriteApproved(wizard, project)) {
      return printResult(init.init(project.root()));
    }
    final InitSelection selection = select(wizard, catalog, project, requestedPreset);
    new InitResultPrinter(output()).printSelectedGates(catalog, project, selection.gates());
    return printResult(init.initialize(project.root(), selection, overwriteOption || exists));
  }

  /** Completes the selection, asking only for what the command-line options leave open. */
  private InitSelection select(
      final InitWizard wizard,
      final InitSetupCatalog catalog,
      final Project project,
      final QualityPreset requestedPreset) {
    final List<String> selectedOptions = gates == null ? List.of() : gates;
    final List<String> providedIntegrations = integrations == null ? List.of() : integrations;
    final boolean gatesGiven = preset != null || !selectedOptions.isEmpty();
    final QualityPreset selectedPreset = gatesGiven ? requestedPreset : wizard.selectPreset();
    final List<String> selectedIntegrations =
        providedIntegrations.isEmpty() ? wizard.selectIntegrations(catalog) : providedIntegrations;
    final List<String> selectedGates =
        gatesGiven ? selectedOptions : wizard.selectGates(catalog, project, selectedPreset);
    final String selectedArchitecture = resolveArchitecture(wizard, catalog, selectedGates);
    return new InitSelection(
        selectedIntegrations, selectedGates, selectedArchitecture, selectedPreset);
  }

  private boolean overwriteApproved(final InitWizard wizard, final Project project) {
    if (input.equals(System.in) && System.console() == null) {
      return false;
    }
    return wizard.confirmOverwrite(project.root().resolve(SentinelConfiguration.FILE_NAME));
  }

  private String resolveArchitecture(
      final InitWizard wizard, final InitSetupCatalog catalog, final List<String> selectedGates) {
    if (selectedGates.stream().noneMatch("archunit"::equals)) {
      return architecture;
    }
    final String selected =
        architecture == null ? wizard.selectArchitecture(catalog) : architecture;
    catalog.architecture(selected);
    return selected;
  }

  private Optional<RawSession> openRawTerminal() {
    if (!input.equals(System.in)
        || System.console() == null
        || System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
      return Optional.empty();
    }
    return rawTerminal.open();
  }

  private int printResult(final InitResult result) {
    new InitResultPrinter(output()).print(result);
    return ExitCodes.OK;
  }

  private PrintWriter output() {
    return spec == null
        ? new PrintWriter(System.out, true, StandardCharsets.UTF_8)
        : spec.commandLine().getOut();
  }
}
