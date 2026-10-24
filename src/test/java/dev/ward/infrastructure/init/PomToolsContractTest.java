package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.QualityGateDefinitions;
import dev.ward.domain.init.ManagedPluginCatalog;
import org.junit.jupiter.api.Test;

/** Every Maven tool belongs to a defined gate, and every plugin has managed versions. */
class PomToolsContractTest {
  @Test
  void everyMavenToolBelongsToADefinedGate() {
    assertThat(PomTools.TOOLS)
        .allSatisfy(
            tool ->
                assertThat(QualityGateDefinitions.find(tool.gate()))
                    .as("Maven tool %s for undefined gate %s", tool.artifact(), tool.gate())
                    .isPresent());
  }

  @Test
  void everyMavenPluginHasManagedVersions() {
    assertThat(PomTools.TOOLS)
        .filteredOn(tool -> !tool.dependency())
        .allSatisfy(
            tool ->
                assertThat(ManagedPluginCatalog.entries())
                    .as(
                        "gate %s: %s has no ManagedPluginCatalog entry",
                        tool.gate(), tool.artifact())
                    .containsKey(tool.artifact()));
  }
}
