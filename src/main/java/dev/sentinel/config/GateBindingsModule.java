package dev.sentinel.config;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dev.sentinel.application.gate.CheckService;
import dev.sentinel.application.spec.SpecValidationService;
import dev.sentinel.domain.gate.FindingExtractor;
import dev.sentinel.domain.gate.ReportDirectories;
import dev.sentinel.domain.gate.ReportSink;
import dev.sentinel.infrastructure.cli.agent.HookChecks;
import dev.sentinel.infrastructure.cli.gate.JsonReportRenderer;
import dev.sentinel.infrastructure.cli.gate.ReportRenderers;
import dev.sentinel.infrastructure.cli.gate.SarifReportRenderer;
import dev.sentinel.infrastructure.cli.gate.TextReportRenderer;
import dev.sentinel.infrastructure.finding.CompositeFindingExtractor;
import dev.sentinel.infrastructure.finding.FileSystemReportDirectories;
import dev.sentinel.infrastructure.report.FileReportSink;

@Module
interface GateBindingsModule {
  @Binds
  FindingExtractor findingExtractor(CompositeFindingExtractor implementation);

  @Binds
  ReportDirectories reportDirectories(FileSystemReportDirectories implementation);

  @Binds
  ReportSink reportSink(FileReportSink implementation);

  @Provides
  static HookChecks hookChecks(final CheckService checks, final SpecValidationService specs) {
    return new HookChecks(checks, specs);
  }

  @Provides
  static ReportRenderers reportRenderers(
      final TextReportRenderer text,
      final JsonReportRenderer json,
      final SarifReportRenderer sarif) {
    return new ReportRenderers(text, json, sarif);
  }
}
