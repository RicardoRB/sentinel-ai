package dev.sentinel.domain.init;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sentinel-managed Maven plugin compatibility catalog. */
public final class ManagedPluginCatalog {
  private ManagedPluginCatalog() {}

  public static Map<String, List<ManagedPluginCandidate>> entries() {
    final Map<String, List<ManagedPluginCandidate>> entries = new LinkedHashMap<>();
    entries.put("maven-checkstyle-plugin", candidates("maven-checkstyle-plugin", "3.6.0", "3.3.1"));
    entries.put("maven-pmd-plugin", candidates("maven-pmd-plugin", "3.28.0", "3.26.0"));
    entries.put(
        "spotbugs-maven-plugin",
        candidates("spotbugs-maven-plugin", "4" + ".9.7.0", "4" + ".8.6.6"));
    entries.put(
        "sonar-maven-plugin", candidates("sonar-maven-plugin", "5.1.0.4751", "3.11.0.3922"));
    entries.put("jacoco-maven-plugin", candidates("jacoco-maven-plugin", "0.8.13", "0.8.8"));
    entries.put("dependency-check-maven", candidates("dependency-check-maven", "12.1.0", "8.4.0"));
    entries.put("pitest-maven", candidates("pitest-maven", "1.17.4", "1.9.11"));
    entries.put("maven-enforcer-plugin", candidates("maven-enforcer-plugin", "3.5.0", "3.0.0"));
    entries.put("spotless-maven-plugin", candidates("spotless-maven-plugin", "3.10.3", "2.44.4"));
    entries.put("license-maven-plugin", candidates("license-maven-plugin", "2.5.0", "2.0.0"));
    entries.put("japicmp-maven-plugin", candidates("japicmp-maven-plugin", "0.23.1", "0.21.2"));
    return Map.copyOf(entries);
  }

  private static List<ManagedPluginCandidate> candidates(
      final String artifact, final String current, final String legacy) {
    final int currentJava =
        "spotless-maven-plugin".equals(artifact) || "sonar-maven-plugin".equals(artifact) ? 11 : 8;
    return List.of(
        new ManagedPluginCandidate(artifact, current, currentJava, 25, "3.6", "default"),
        new ManagedPluginCandidate(artifact, legacy, 8, 25, "3.5", "legacy"));
  }
}
