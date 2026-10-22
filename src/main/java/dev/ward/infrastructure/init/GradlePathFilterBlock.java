package dev.ward.infrastructure.init;

/**
 * The opt-in source filter init adds after the Gradle Checkstyle and PMD blocks, so that a partial
 * check can analyze only the edited files.
 */
final class GradlePathFilterBlock {
  private GradlePathFilterBlock() {}

  /**
   * Narrows the task to the files in the {@code wardPaths} property, root-relative and
   * comma-separated, which a partial check passes. Without the property the task is unchanged.
   */
  static String render(final boolean kotlin, final String task, final String type) {
    if (kotlin) {
      return "tasks.named<"
          + type
          + ">(\""
          + task
          + "\") {\n"
          + "    val wardPaths = providers.gradleProperty(\"wardPaths\")\n"
          + "    if (wardPaths.isPresent) {\n"
          + "        val paths = wardPaths.get().split(\",\")\n"
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
        + "    def wardPaths = providers.gradleProperty('wardPaths')\n"
        + "    if (wardPaths.isPresent()) {\n"
        + "        def paths = wardPaths.get().split(',') as List\n"
        + "        def root = layout.projectDirectory.asFile.toPath()\n"
        + "        setSource(source.filter {"
        + " paths.contains(root.relativize(it.toPath()).toString().replace('\\\\', '/')) })\n"
        + "    }\n"
        + "}";
  }
}
