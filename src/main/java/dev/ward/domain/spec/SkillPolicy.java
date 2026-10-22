package dev.ward.domain.spec;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Recognizes agent skills and commands that archive or sync into approved OpenSpec specs. */
public final class SkillPolicy {
  private static final Set<String> NAMESPACES = Set.of("openspec", "opsx");
  private static final Set<String> ACTIONS = Set.of("archive", "sync");

  private SkillPolicy() {}

  /**
   * Returns whether {@code name} is an OpenSpec archive or sync skill, in any namespaced form such
   * as {@code opsx:archive}, {@code opsx-sync}, {@code openspec-archive-change}, or {@code
   * plugin:openspec-sync-specs}.
   */
  public static boolean archivesOrSyncs(final String name) {
    if (name == null || name.isBlank()) {
      return false;
    }
    final Set<String> words =
        Arrays.stream(name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
            .collect(Collectors.toSet());
    return words.stream().anyMatch(NAMESPACES::contains)
        && words.stream().anyMatch(ACTIONS::contains);
  }
}
