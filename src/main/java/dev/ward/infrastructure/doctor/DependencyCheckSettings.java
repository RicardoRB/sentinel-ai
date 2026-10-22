package dev.ward.infrastructure.doctor;

import dev.ward.domain.doctor.OssIndexSetting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/** Reads the OSS Index analyzer setting from a Maven or Gradle build file without running it. */
final class DependencyCheckSettings {
  private static final List<String> BUILD_FILES =
      List.of("pom.xml", "build.gradle", "build.gradle.kts");
  private static final Pattern MAVEN_DISABLED =
      Pattern.compile("<ossindexAnalyzerEnabled>\\s*false\\s*</ossindexAnalyzerEnabled>");
  private static final Pattern GRADLE_DISABLED =
      Pattern.compile("ossIndex\\s*\\{[^}]*\\benabled\\s*=\\s*false|ossIndexEnabled\\s*=\\s*false");

  private DependencyCheckSettings() {}

  static OssIndexSetting read(final Path projectRoot) {
    for (final String name : BUILD_FILES) {
      final Path file = projectRoot.resolve(name);
      if (Files.isRegularFile(file)) {
        try {
          return setting(name, Files.readString(file));
        } catch (IOException e) {
          return OssIndexSetting.UNREADABLE;
        }
      }
    }
    return OssIndexSetting.ABSENT;
  }

  private static OssIndexSetting setting(final String name, final String content) {
    final boolean maven = "pom.xml".equals(name);
    final boolean configured =
        maven
            ? content.contains("<artifactId>dependency-check-maven</artifactId>")
            : content.contains("org.owasp.dependencycheck");
    if (!configured) {
      return OssIndexSetting.ABSENT;
    }
    final Pattern disabled = maven ? MAVEN_DISABLED : GRADLE_DISABLED;
    return disabled.matcher(content).find() ? OssIndexSetting.DISABLED : OssIndexSetting.ENABLED;
  }
}
