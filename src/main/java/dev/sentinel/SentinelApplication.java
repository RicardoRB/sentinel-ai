package dev.sentinel;

import dev.sentinel.application.CheckService;
import dev.sentinel.application.InitService;
import dev.sentinel.application.ProjectDetector;
import dev.sentinel.application.QualityGateFactory;
import dev.sentinel.application.QualityGateRunner;
import dev.sentinel.cli.CommandLineRunnerImpl;
import dev.sentinel.cli.JsonReportRenderer;
import dev.sentinel.cli.TextReportRenderer;
import dev.sentinel.infrastructure.ProcessCommandExecutor;
import dev.sentinel.infrastructure.TomlConfigurationReader;

public class SentinelApplication {

    public static void main(String[] args) {
        ProcessCommandExecutor executor = new ProcessCommandExecutor();
        ProjectDetector detector = new ProjectDetector();
        QualityGateFactory factory = new QualityGateFactory(executor);
        CheckService checks = new CheckService(detector, new TomlConfigurationReader(), factory,
                new QualityGateRunner());
        CommandLineRunnerImpl runner = new CommandLineRunnerImpl(checks, new InitService(detector),
                detector, new TextReportRenderer(), new JsonReportRenderer());
        System.exit(runner.run(args));
    }
}
