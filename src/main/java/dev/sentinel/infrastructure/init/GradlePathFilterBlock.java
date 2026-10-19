package dev.sentinel.infrastructure.init;

/**
 * The opt-in source filter init adds after the Gradle Checkstyle and PMD blocks, so that a partial
 * check can analyze only the edited files.
 */
final class GradlePathFilterBlock {
  private GradlePathFilterBlock() {}

  /**
   * Narrows the task to the files in the {@code sentinelPaths} property, root-relative and
   * comma-separated, which a partial check passes. Without the property the task is unchanged.
   */
  static String render(final boolean kotlin, final String task, final String type) {
    if (kotlin) {
      return "tasks.named<"
          + type
          + ">(\""
          + task
          + "\") {\n"
          + "    val sentinelPaths = providers.gradleProperty(\"sentinelPaths\")\n"
          + "    if (sentinelPaths.isPresent) {\n"
          + "        val paths = sentinelPaths.get().split(\",\")\n"
          + "        val root = layout.projectDirectory.asFile\n"
          + "        setSource(source.filter {"
          + " paths.contains(it.relativeTo(root).invariantSeparatorsPath) })\n"
          + "    }\n"
          + "}";
    }
    return "tasks.named('"
        + task
        + "', "
        + type
        + ") {\n"
        + "    def sentinelPaths = providers.gradleProperty('sentinelPaths')\n"
        + "    if (sentinelPaths.isPresent()) {\n"
        + "        def paths = sentinelPaths.get().split(',') as List\n"
        + "        def root = layout.projectDirectory.asFile.toPath()\n"
        + "        setSource(source.filter {"
        + " paths.contains(root.relativize(it.toPath()).toString().replace('\\\\', '/')) })\n"
        + "    }\n"
        + "}";
  }
}
