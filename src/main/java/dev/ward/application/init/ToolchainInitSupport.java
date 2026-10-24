package dev.ward.application.init;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.util.List;

/** The init choices that depend on a project's toolchain. */
interface ToolchainInitSupport {
  /** Returns the gates that run through the toolchain's build tool, with availability resolved. */
  Gates gates(Project project, EnvironmentInspection environment);

  /** Returns the gate IDs {@code preset} enables for {@code project}, in configuration order. */
  List<String> presetGates(Project project, QualityPreset preset);

  /** Returns the CI workflow steps that set up the toolchain, each line ending in a newline. */
  String ciSetup(Project project);

  /**
   * Build-tool gates in wizard order, split around the toolchain-independent binary-tool gates.
   *
   * @param analysis the gates that build, test and analyze the project
   * @param policy the gates that enforce build, license, compatibility or custom policies
   */
  record Gates(List<InitGateOption> analysis, List<InitGateOption> policy) {
    /** Copies the lists so the gates stay immutable. */
    public Gates {
      analysis = List.copyOf(analysis);
      policy = List.copyOf(policy);
    }
  }
}
