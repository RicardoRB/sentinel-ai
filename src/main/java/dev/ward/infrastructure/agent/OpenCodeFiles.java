package dev.ward.infrastructure.agent;

import dev.ward.domain.project.Project;

/**
 * The OpenCode files of one Ward project. A root project keeps the original names and contents; a
 * nested project, installed at the repository root, gets files suffixed with its path slug whose
 * plugins run Ward from the project directory, found from the plugin's own location.
 *
 * @param projectPath the {@code /}-separated project path below the install root, or empty
 */
record OpenCodeFiles(String projectPath) {
  static final String COMMAND_FILE = ".opencode/commands/ward-check";
  static final String PLUGIN_FILE = ".opencode/plugins/ward-edit-write";
  static final String GUARD_FILE = ".opencode/plugins/ward-openspec-guard";
  private static final String SESSION_CWD = "{ cwd: directory, ";
  private static final String PROJECT_CWD = "{ cwd: PROJECT, ";

  private boolean nested() {
    return !projectPath.isEmpty();
  }

  private String suffix() {
    return nested() ? "-" + Project.slug(projectPath) : "";
  }

  String commandFile() {
    return COMMAND_FILE + suffix() + ".md";
  }

  String pluginFile() {
    return PLUGIN_FILE + suffix() + ".js";
  }

  String guardFile() {
    return GUARD_FILE + suffix() + ".js";
  }

  String command(final String template) {
    return nested()
        ? template.replace("`ward check --format", "`ward check -C " + projectPath + " --format")
        : template;
  }

  /** Runs Ward from the project, with edited paths made absolute against the session directory. */
  String plugin(final String template) {
    if (!nested()) {
      return template;
    }
    return template
        .replace(
            "import { Plugin } from \"@opencode/plugin\";",
            "import { dirname, isAbsolute, join } from \"node:path\";\n"
                + "import { fileURLToPath } from \"node:url\";\n"
                + "import { Plugin } from \"@opencode/plugin\";")
        .replace("const EDIT_TOOLS", projectConstant() + "const EDIT_TOOLS")
        .replace(SESSION_CWD, PROJECT_CWD)
        .replace(
            "const paths = editedPaths(event.input, event.result);",
            "const paths = editedPaths(event.input, event.result)"
                + ".map((path) => (isAbsolute(path) ? path : join(directory, path)));")
        .replace(
            "id: \"ward.edit-write\"", "id: \"ward.edit-write." + Project.slug(projectPath) + "\"");
  }

  /** Runs the guard from the project, with an edited path made absolute against the session. */
  String guard(final String template) {
    if (!nested()) {
      return template;
    }
    return template
        .replace(
            "import { isAbsolute, join } from \"node:path\";",
            "import { dirname, isAbsolute, join } from \"node:path\";\n"
                + "import { fileURLToPath } from \"node:url\";")
        .replace("const GUARDED", projectConstant() + "const GUARDED")
        .replace(SESSION_CWD, PROJECT_CWD)
        .replace(
            "if (path) return { command: [\"spec\", \"guard\", \"--\", path] };",
            "if (path) return { command: [\"spec\", \"guard\", \"--\","
                + " isAbsolute(path) ? path : join(directory, path)] };")
        .replace(
            "id: \"ward.openspec-guard\"",
            "id: \"ward.openspec-guard." + Project.slug(projectPath) + "\"");
  }

  /** The project directory: two levels above the plugin file, then the project path. */
  private String projectConstant() {
    return "// Ward checks the project this repository-level plugin was installed for.\n"
        + "const PROJECT = join(dirname(fileURLToPath(import.meta.url)), \"..\", \"..\", "
        + jsString(projectPath)
        + ");\n";
  }

  private static String jsString(final String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
