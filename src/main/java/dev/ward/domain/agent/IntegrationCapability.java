package dev.ward.domain.agent;

/** A lifecycle or enforcement capability exposed by an agent integration. */
public enum IntegrationCapability {
  PRE_TOOL,
  POST_EDIT,
  TURN_END,
  FEEDBACK,
  APPROVED_ARTIFACT_GUARD
}
