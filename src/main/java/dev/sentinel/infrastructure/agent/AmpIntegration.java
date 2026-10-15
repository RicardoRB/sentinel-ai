package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationCapability;
import java.util.Set;

/** Native Amp TypeScript plugin integration contract. */
public final class AmpIntegration implements NativeHarnessAdapter {
  private static final NativeAgentIntegration.Definition DEFINITION =
      new NativeAgentIntegration.Definition(
          "amp",
          ".amp/plugins/sentinel.ts",
          ".amp/plugins/sentinel.sh",
          "tool.call,tool.result,agent.end",
          Set.of(
              IntegrationCapability.PRE_TOOL,
              IntegrationCapability.POST_EDIT,
              IntegrationCapability.TURN_END,
              IntegrationCapability.FEEDBACK,
              IntegrationCapability.APPROVED_ARTIFACT_GUARD));
  private final NativeIntegrationSupport support = new NativeIntegrationSupport(DEFINITION);

  @Override
  public NativeAgentIntegration.Definition definition() {
    return DEFINITION;
  }

  @Override
  public NativeIntegrationSupport support() {
    return support;
  }

  @Override
  public String configContent() {
    return "// "
        + NativeIntegrationTemplates.marker(DEFINITION)
        + "\n"
        + "export default function (amp) {\n"
        + "  amp.on('tool.call', async () => ({ action: 'allow' }));\n"
        + "  amp.on('tool.result', async () => undefined);\n"
        + "  amp.on('agent.end', async () => undefined);\n"
        + "}\n";
  }

  @Override
  public String denyResponse(final String reason) {
    return "{\"action\":\"reject-and-continue\",\"message\":" + quote(reason) + "}";
  }

  private static String quote(final String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
