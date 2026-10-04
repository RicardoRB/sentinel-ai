package dev.sentinel.cli;

import dev.sentinel.application.CheckService;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.CheckReport;
import com.google.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

@Command(name = "check", description = "Run all enabled quality gates from sentinel.toml.",
        mixinStandardHelpOptions = true, versionProvider = VersionProvider.class,
        footer = {"", "Exit codes: 0 = all gates passed, 1 = at least one gate failed, 2 = error"})
public class CheckCommand implements Callable<Integer> {

    public enum Format { text, json }

    @Spec
    private CommandSpec spec;
    @Mixin
    private ProjectOptions options = new ProjectOptions();

    @Option(names = "--format", paramLabel = "<format>", defaultValue = "text",
            description = "Output format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private Format format;

    private final CheckService service;
    private final TextReportRenderer textRenderer;
    private final JsonReportRenderer jsonRenderer;

    @Inject
    public CheckCommand(CheckService service, TextReportRenderer textRenderer, JsonReportRenderer jsonRenderer) {
        this.service = service;
        this.textRenderer = textRenderer;
        this.jsonRenderer = jsonRenderer;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        CheckReport report;
        try {
            report = service.check(options.directory());
        } catch (SentinelException e) {
            if (format == Format.json) {
                // stdout stays machine-readable; the human message goes to stderr as well.
                out.println(jsonRenderer.renderError(e.getMessage()));
            }
            throw e;
        }
        out.print(format == Format.json ? jsonRenderer.render(report) + System.lineSeparator()
                : textRenderer.render(report));
        return report.passed() ? ExitCodes.OK : ExitCodes.FAILED;
    }
}
