package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dev.ward.application.agent.HookScope;
import dev.ward.application.gate.CheckService;
import dev.ward.application.project.ProjectDetector;
import dev.ward.application.spec.SpecValidationService;
import dev.ward.domain.gate.FindingExtractor;
import dev.ward.domain.gate.ReportDirectories;
import dev.ward.domain.gate.ReportSink;
import dev.ward.domain.project.ProjectChanges;
import dev.ward.domain.spec.ProjectPaths;
import dev.ward.infrastructure.cli.agent.HookChecks;
import dev.ward.infrastructure.cli.gate.JsonReportRenderer;
import dev.ward.infrastructure.cli.gate.ReportRenderers;
import dev.ward.infrastructure.cli.gate.SarifReportRenderer;
import dev.ward.infrastructure.cli.gate.TextReportRenderer;
import dev.ward.infrastructure.finding.CompositeFindingExtractor;
import dev.ward.infrastructure.finding.FileSystemReportDirectories;
import dev.ward.infrastructure.project.GitProjectChanges;
import dev.ward.infrastructure.report.FileReportSink;

@Module
interface GateBindingsModule {
  @Binds
  FindingExtractor findingExtractor(CompositeFindingExtractor implementation);

  @Binds
  ReportDirectories reportDirectories(FileSystemReportDirectories implementation);

  @Binds
  ReportSink reportSink(FileReportSink implementation);

  @Binds
  ProjectChanges projectChanges(GitProjectChanges implementation);

  @Provides
  static HookChecks hookChecks(
      final CheckService checks,
      final SpecValidationService specs,
      final ProjectDetector detector,
      final ProjectChanges changes,
      final ProjectPaths paths) {
    return new HookChecks(checks, specs, new HookScope(detector, changes, paths));
  }

  @Provides
  static ReportRenderers reportRenderers(
      final TextReportRenderer text,
      final JsonReportRenderer json,
      final SarifReportRenderer sarif) {
    return new ReportRenderers(text, json, sarif);
  }
}
