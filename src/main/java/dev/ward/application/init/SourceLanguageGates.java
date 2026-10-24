package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.List;

/**
 * The gates of one source language inside its toolchain. A toolchain support keeps one per
 * language, keyed by {@link dev.ward.domain.project.Language}.
 *
 * @param <C> what the toolchain builds gates from, such as the build tool and its executable
 */
interface SourceLanguageGates<C> {
  /** Returns the language's analysis gates, in wizard order, built from {@code toolchain}. */
  List<InitGateOption> analysisGates(C toolchain);

  /** Returns the gate IDs that {@code preset} enables for the language, in configuration order. */
  List<String> presetGates(QualityPreset preset);
}
