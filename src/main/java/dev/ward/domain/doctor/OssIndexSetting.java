package dev.ward.domain.doctor;

/** Whether the project's Dependency-Check build configuration uses the OSS Index analyzer. */
public enum OssIndexSetting {
  /** The build does not configure Dependency-Check. */
  ABSENT,
  /** Dependency-Check is configured with the OSS Index analyzer turned off. */
  DISABLED,
  /** Dependency-Check is configured and OSS Index is on, explicitly or by the plugin default. */
  ENABLED,
  /** The build file could not be read, so the setting is unknown. */
  UNREADABLE
}
