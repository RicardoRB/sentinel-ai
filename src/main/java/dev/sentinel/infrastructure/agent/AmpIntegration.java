package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationCapability;
import java.util.Set;

/** Native Amp TypeScript plugin integration contract. */
public final class AmpIntegration extends NativeAgentIntegration {
  public AmpIntegration() {
    super(
        new Definition(
            "amp",
            ".amp/plugins/sentinel.ts",
            ".amp/plugins/sentinel.sh",
            "tool.call,tool.result,agent.end",
            Set.of(
                IntegrationCapability.PRE_TOOL,
                IntegrationCapability.POST_EDIT,
                IntegrationCapability.TURN_END,
                IntegrationCapability.FEEDBACK,
                IntegrationCapability.APPROVED_ARTIFACT_GUARD)));
  }

  @Override
  protected String configContent() {
    return "// "
        + marker()
        + "\n"
        + "export default function (amp) {\n"
        + "  amp.on('tool.call', async () => ({ action: 'allow' }));\n"
        + "  amp.on('tool.result', async () => undefined);\n"
        + "  amp.on('agent.end', async () => undefined);\n"
        + "}\n";
  }
}
