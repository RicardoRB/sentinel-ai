package dev.ward.domain.init;

import java.util.List;

/** Explicit user intent to install a selected project tool. */
public record ToolInstallationRequest(List<String> tools, boolean consented) {
  /** Copies the tools. */
  public ToolInstallationRequest {
    tools = List.copyOf(tools);
  }
}
