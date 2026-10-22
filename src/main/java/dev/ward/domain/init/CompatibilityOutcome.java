package dev.ward.domain.init;

/** Classification produced while resolving a managed Maven plugin version. */
public enum CompatibilityOutcome {
  COMPATIBLE,
  UPGRADE_RECOMMENDED,
  INCOMPATIBLE,
  UNKNOWN,
  UNRESOLVABLE
}
