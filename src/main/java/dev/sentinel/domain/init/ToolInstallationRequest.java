package dev.sentinel.domain.init;

import java.util.List;

/** Explicit user intent to install a selected project tool. */
public record ToolInstallationRequest(List<String> tools, boolean consented) {
  public ToolInstallationRequest {
    tools = List.copyOf(tools);
  }
}
