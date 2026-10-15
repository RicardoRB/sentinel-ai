package dev.sentinel.config;

import dagger.Binds;
import dagger.Module;
import dev.sentinel.domain.gate.FindingExtractor;
import dev.sentinel.domain.gate.ReportDirectories;
import dev.sentinel.domain.gate.ReportSink;
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
}
