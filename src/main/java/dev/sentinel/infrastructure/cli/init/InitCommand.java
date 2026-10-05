package dev.sentinel.infrastructure.cli.init;

import dev.sentinel.domain.init.InitArchitectureOption;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitIntegrationOption;
import dev.sentinel.domain.init.InitResult;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.application.init.InitService;
import dev.sentinel.application.init.InitSetupCatalog;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
import dev.sentinel.infrastructure.cli.VersionProvider;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelException;
import com.google.inject.Inject;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

@Command(name = "init", description = "Create sentinel.toml with an interactive setup wizard.",
        mixinStandardHelpOptions = true, versionProvider = VersionProvider.class)
public class InitCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;
    @Mixin
    private ProjectOptions options = new ProjectOptions();
    @Option(names = "--integration", split = ",", description = "Agent integration(s): none, opencode, or claude-code. Repeat or comma-separate.")
    private List<String> integrations = new ArrayList<>();
    @Option(names = "--gate", split = ",", description = "Quality gate(s) to enable. Repeat or comma-separate.")
    private List<String> gates = new ArrayList<>();
    @Option(names = "--architecture", description = "Architecture style for architecture/ArchUnit tests: layered, hexagonal, or clean.")
    private String architecture;
    @Option(names = "--overwrite", description = "Overwrite an existing sentinel.toml without prompting.")
    private boolean overwriteOption;

    private final Supplier<InitService> service;
    private final InputStream input;
    private final BufferedReader reader;

    @Inject
    public InitCommand(InitService service) {
        this(service, System.in);
    }

    public InitCommand(InitService service, InputStream input) {
        this.service = () -> service;
        this.input = input;
        this.reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    @Override
    public Integer call() {
        Path start = options.directory();
        var project = service.get().project(start);
        boolean overwrite = overwriteOption;
        if (service.get().configurationExists(project)) {
            if (!overwrite) {
                if (input == System.in && System.console() == null) {
            return printResult(service.get().init(project.root()));
            }
                if (!confirmOverwrite(project.root().resolve(SentinelConfiguration.FILE_NAME))) {
                    return printResult(service.get().init(project.root()));
                }
                overwrite = true;
            }
        }

        InitSetupCatalog catalog = service.get().catalog();
        List<String> selectedIntegrations = integrations.isEmpty() ? selectIntegrations(catalog) : integrations;
        List<String> selectedGates = gates.isEmpty() ? selectGates(catalog, project) : gates;
        String selectedArchitecture = architecture;
        if (requiresArchitectureTest(selectedGates)) {
            selectedArchitecture = selectedArchitecture == null
                    ? selectArchitecture(catalog) : selectedArchitecture;
            catalog.architecture(selectedArchitecture);
        }
        PrintWriter out = output();
        for (String selectedGate : selectedGates) {
            InitGateOption gateOption = catalog.gate(project, selectedGate);
            out.printf("Selected quality gate '%s': %s%n", gateOption.id(),
                    gateOption.available() ? "AVAILABLE" : "UNAVAILABLE");
            out.println(gateOption.availabilityMessage());
        }
        InitResult result = service.get().initialize(project.root(),
                new InitSelection(selectedIntegrations, selectedGates, selectedArchitecture), overwrite);
        return printResult(result);
    }

    private List<String> selectIntegrations(InitSetupCatalog catalog) {
        if (input == System.in && System.console() != null
                && !System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return selectIntegrationsWithKeys(catalog);
        }
        PrintWriter out = output();
        List<InitIntegrationOption> choices = catalog.integrations();
        out.println("Select agent integrations (enter numbers separated by spaces, then press Enter):");
        for (int i = 0; i < choices.size(); i++) {
            out.printf("> [ ] %d) %s%n", i + 1, choices.get(i).label());
        }
        return readIntegrationChoices(choices);
    }

    private boolean confirmOverwrite(Path file) {
        PrintWriter out = output();
        out.print(file + " already exists. Overwrite it? [y/N]: ");
        out.flush();
        try {
            String answer = reader.readLine();
            boolean overwrite = answer != null && answer.trim().equalsIgnoreCase("y");
            if (!overwrite) out.println("Keeping existing sentinel.toml. No changes made.");
            return overwrite;
        } catch (IOException e) {
            out.println("Keeping existing sentinel.toml. No changes made.");
            return false;
        }
    }

    private List<String> selectIntegrationsWithKeys(InitSetupCatalog catalog) {
        List<InitIntegrationOption> choices = catalog.integrations();
        boolean[] selected = new boolean[choices.size()];
        int cursor = 0;
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            var originalAttributes = terminal.enterRawMode();
            try {
            renderIntegrationChoices(catalog, selected, cursor);
            while (true) {
                int key = terminal.reader().read();
                if (key < 0) throw new SentinelException("Initialization cancelled: input ended before setup completed.");
                if (key == ' ') {
                    selected[cursor] = !selected[cursor];
                    renderIntegrationChoices(catalog, selected, cursor);
                } else if (key == '\n' || key == '\r') {
                    return selectedIntegrations(choices, selected);
                } else if (key == 'q' || key == 'Q' || key == 3) {
                    throw new SentinelException("Initialization cancelled by the user.");
                } else if (key == 27) {
                    int bracket = terminal.reader().read();
                    int direction = bracket == '[' ? terminal.reader().read() : -1;
                    if (direction == 'A') cursor = (cursor + choices.size() - 1) % choices.size();
                    if (direction == 'B') cursor = (cursor + 1) % choices.size();
                    renderIntegrationChoices(catalog, selected, cursor);
                }
            }
            } finally {
                terminal.setAttributes(originalAttributes);
                output().println();
            }
        } catch (IOException e) {
            throw new SentinelException("Could not read initialization selection: " + e.getMessage());
        }
    }

    private void renderIntegrationChoices(InitSetupCatalog catalog, boolean[] selected, int cursor) {
        PrintWriter out = output();
        out.print("\033[2J\033[H");
        out.println("Select agent integrations (Space toggles, arrows move, Enter confirms):");
        List<InitIntegrationOption> choices = catalog.integrations();
        for (int i = 0; i < choices.size(); i++) {
            out.printf("%s %s %d) %s%n", i == cursor ? ">" : " ",
                    selected[i] ? "[x]" : "[ ]", i + 1, choices.get(i).label());
        }
        out.flush();
    }

    private List<String> selectedIntegrations(List<InitIntegrationOption> choices, boolean[] selected) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < selected.length; i++) if (selected[i]) result.add(choices.get(i).id());
        if (result.isEmpty()) throw new SentinelException("Select at least one integration, or choose none.");
        return result;
    }

    private List<String> selectGates(InitSetupCatalog catalog, dev.sentinel.domain.project.Project project) {
        PrintWriter out = output();
        List<InitGateOption> choices = catalog.gates(project);
        if (input == System.in && System.console() != null
                && !System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return selectGatesWithKeys(choices);
        }
        out.println("Select quality gates (enter numbers separated by spaces, then press Enter):");
        for (int i = 0; i < choices.size(); i++) {
            InitGateOption choice = choices.get(i);
            out.printf("> [ ] %d) %s - %s [%s]%n", i + 1, choice.id(), choice.description(),
                    choice.available() ? "available" : "unavailable");
        }
        return readGateChoices(choices);
    }

    private String selectArchitecture(InitSetupCatalog catalog) {
        PrintWriter out = output();
        List<InitArchitectureOption> choices = catalog.architectures();
        out.println("Select an architecture style:");
        for (int i = 0; i < choices.size(); i++) {
            out.printf("> %d) %s%n", i + 1, choices.get(i).label());
        }
        out.print("Choose an architecture [1-" + choices.size() + "]: ");
        out.flush();
        try {
            String value = reader.readLine();
            if (value == null) throw new SentinelException("Initialization cancelled: input ended before setup completed.");
            int choice;
            try {
                choice = Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                throw new SentinelException("Invalid architecture selection '" + value + "'.");
            }
            if (choice < 1 || choice > choices.size()) {
                throw new SentinelException("Invalid architecture selection. Choose a number from 1 to " + choices.size() + ".");
            }
            return choices.get(choice - 1).id();
        } catch (IOException e) {
            throw new SentinelException("Could not read architecture selection: " + e.getMessage());
        }
    }

    private static boolean requiresArchitectureTest(List<String> selectedGates) {
        return selectedGates.stream().anyMatch(gate -> gate.equals("archunit"));
    }

    private int readChoice(String prompt, int size) {
        PrintWriter out = output();
        out.print(prompt);
        out.flush();
        try {
            String value = reader.readLine();
            if (value == null) {
                throw new SentinelException("Initialization cancelled: input ended before setup completed.");
            }
            try {
                int choice = Integer.parseInt(value.trim());
                if (choice < 1 || choice > size) throw new NumberFormatException();
                return choice - 1;
            } catch (NumberFormatException e) {
                throw new SentinelException("Invalid selection '" + value + "'. Choose a number from 1 to " + size + ".");
            }
        } catch (IOException e) {
            throw new SentinelException("Could not read initialization selection: " + e.getMessage());
        }
    }

    private List<String> readIntegrationChoices(List<InitIntegrationOption> choices) {
        PrintWriter out = output();
        out.print("Toggle integrations with space-separated numbers [1-" + choices.size() + "]: ");
        out.flush();
        try {
            String value = reader.readLine();
            if (value == null) {
                throw new SentinelException("Initialization cancelled: input ended before setup completed.");
            }
            List<String> selected = new ArrayList<>();
            for (String token : value.trim().split("[ ,]+")) {
                if (token.isBlank()) continue;
                try {
                    int choice = Integer.parseInt(token);
                    if (choice < 1 || choice > choices.size()) throw new NumberFormatException();
                    String id = choices.get(choice - 1).id();
                    if (!selected.contains(id)) selected.add(id);
                } catch (NumberFormatException e) {
                    throw new SentinelException("Invalid integration selection '" + token
                            + "'. Choose numbers from 1 to " + choices.size() + ".");
                }
            }
            if (selected.isEmpty()) {
                throw new SentinelException("Select at least one integration, or choose 1 for none.");
            }
            return selected;
        } catch (IOException e) {
            throw new SentinelException("Could not read initialization selection: " + e.getMessage());
        }
    }

    private List<String> readGateChoices(List<InitGateOption> choices) {
        PrintWriter out = output();
        out.print("Toggle quality gates with space-separated numbers [1-" + choices.size() + "]: ");
        out.flush();
        try {
            String value = reader.readLine();
            if (value == null) throw new SentinelException("Initialization cancelled: input ended before setup completed.");
            List<String> selected = new ArrayList<>();
            for (String token : value.trim().split("[ ,]+")) {
                if (token.isBlank()) continue;
                try {
                    int choice = Integer.parseInt(token);
                    if (choice < 1 || choice > choices.size()) throw new NumberFormatException();
                    String id = choices.get(choice - 1).id();
                    if (!selected.contains(id)) selected.add(id);
                } catch (NumberFormatException e) {
                    throw new SentinelException("Invalid quality-gate selection '" + token
                            + "'. Choose numbers from 1 to " + choices.size() + ".");
                }
            }
            if (selected.isEmpty()) throw new SentinelException("Select at least one quality gate.");
            return selected;
        } catch (IOException e) {
            throw new SentinelException("Could not read initialization selection: " + e.getMessage());
        }
    }

    private List<String> selectGatesWithKeys(List<InitGateOption> choices) {
        boolean[] selected = new boolean[choices.size()];
        int cursor = 0;
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            var originalAttributes = terminal.enterRawMode();
            try {
            renderGateChoices(choices, selected, cursor);
            while (true) {
                int key = terminal.reader().read();
                if (key < 0) throw new SentinelException("Initialization cancelled: input ended before setup completed.");
                if (key == ' ') {
                    selected[cursor] = !selected[cursor];
                    renderGateChoices(choices, selected, cursor);
                } else if (key == '\n' || key == '\r') {
                    return selectedGates(choices, selected);
                } else if (key == 'q' || key == 'Q' || key == 3) {
                    throw new SentinelException("Initialization cancelled by the user.");
                } else if (key == 27) {
                    int bracket = terminal.reader().read();
                    int direction = bracket == '[' ? terminal.reader().read() : -1;
                    if (direction == 'A') cursor = (cursor + choices.size() - 1) % choices.size();
                    if (direction == 'B') cursor = (cursor + 1) % choices.size();
                    renderGateChoices(choices, selected, cursor);
                }
            }
            } finally {
                terminal.setAttributes(originalAttributes);
                output().println();
            }
        } catch (IOException e) {
            throw new SentinelException("Could not read initialization selection: " + e.getMessage());
        }
    }

    private void renderGateChoices(List<InitGateOption> choices, boolean[] selected, int cursor) {
        PrintWriter out = output();
        out.print("\033[2J\033[H");
        out.println("Select quality gates (Space toggles, arrows move, Enter confirms):");
        for (int i = 0; i < choices.size(); i++) {
            out.printf("%s %s %d) %s - %s [%s]%n", i == cursor ? ">" : " ",
                    selected[i] ? "[x]" : "[ ]", i + 1, choices.get(i).id(), choices.get(i).description(),
                    choices.get(i).available() ? "available" : "unavailable");
        }
        out.flush();
    }

    private List<String> selectedGates(List<InitGateOption> choices, boolean[] selected) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < selected.length; i++) if (selected[i]) result.add(choices.get(i).id());
        if (result.isEmpty()) throw new SentinelException("Select at least one quality gate.");
        return result;
    }

    private int printResult(InitResult result) {
        PrintWriter out = output();
        if (result.created()) {
            out.println((result.overwritten() ? "Updated " : "Created ") + result.file());
            if (!result.pomChanges().isEmpty()) {
                out.println("Updated pom.xml with Maven tools:");
                result.pomChanges().forEach(tool -> out.println("- " + tool));
            }
            if (result.architectureTest() != null) {
                if (result.architectureTest().created()) {
                    out.println("Generated " + result.architectureTest().architecture()
                            + " ArchUnit test: " + result.architectureTest().file());
                } else {
                    out.println("Preserved existing ArchUnit test: " + result.architectureTest().file());
                }
            }
            if (result.integrations().isEmpty()) {
                out.println("No agent integration installed.");
            } else {
                for (var integration : result.integrations()) {
                    out.printf("Integration: %s%n", integration.message());
                    integration.changed().forEach(out::println);
                }
            }
        } else {
            out.println(result.file() + " already exists. Nothing was changed.");
        }
        return ExitCodes.OK;
    }

    private PrintWriter output() {
        return spec == null
                ? new PrintWriter(System.out, true, StandardCharsets.UTF_8)
                : spec.commandLine().getOut();
    }
}
