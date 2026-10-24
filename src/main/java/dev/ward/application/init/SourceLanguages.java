package dev.ward.application.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The source-language gates of a project, resolved from its toolchain's language map. A project
 * with several languages gets the gates of each, primary language first, each gate once.
 *
 * @param <C> what the toolchain builds gates from
 */
final class SourceLanguages<C> {
  private final List<SourceLanguageGates<C>> languages;

  private SourceLanguages(final List<SourceLanguageGates<C>> languages) {
    this.languages = List.copyOf(languages);
  }

  /**
   * Returns the gates of every language of {@code project}, primary first.
   *
   * @throws WardException if a language has no entry in {@code gates}
   */
  static <C> SourceLanguages<C> of(
      final Map<Language, SourceLanguageGates<C>> gates, final Project project) {
    return new SourceLanguages<>(
        Stream.concat(Stream.of(project.language()), project.languages().stream())
            .distinct()
            .map(
                language -> {
                  final SourceLanguageGates<C> entry = gates.get(language);
                  if (entry == null) {
                    throw new WardException(
                        "Ward init does not support "
                            + language.displayName()
                            + " "
                            + project.buildTool().displayName()
                            + " projects yet.");
                  }
                  return entry;
                })
            .distinct()
            .toList());
  }

  /** Returns the analysis gates of every language, each gate ID once, in first-seen order. */
  List<InitGateOption> analysisGates(final C toolchain) {
    final Map<String, InitGateOption> byId = new LinkedHashMap<>();
    languages.forEach(
        language ->
            language.analysisGates(toolchain).forEach(gate -> byId.putIfAbsent(gate.id(), gate)));
    return List.copyOf(byId.values());
  }

  /** Returns the gate IDs {@code preset} enables across every language, each once. */
  List<String> presetGates(final QualityPreset preset) {
    return languages.stream()
        .flatMap(language -> language.presetGates(preset).stream())
        .distinct()
        .toList();
  }
}
