# Spec Delta

## ADDED Requirements

### Requirement: Maven declarations target the project-level build

When initialization adds a Maven plugin, Sentinel SHALL place it in the project's own build plugins, and when it adds a dependency, Sentinel SHALL place it in the project's own dependencies. It SHALL NOT place either inside a profile, plugin management, dependency management, or another plugin's dependencies. Sentinel SHALL create missing build, plugins, or dependencies sections and SHALL leave every other byte of `pom.xml` unchanged.

#### Scenario: Profile declared before the project build
- **WHEN** `pom.xml` declares a profile with its own build plugins before the project-level build
- **THEN** the generated plugin is added to the project-level build plugins and the profile is unchanged

#### Scenario: Plugin management and dependency management are present
- **WHEN** `pom.xml` contains plugin management, dependency management, or plugin-level dependencies
- **THEN** generated plugins and dependencies are added only to the project-level sections

#### Scenario: Build section is missing or empty
- **WHEN** `pom.xml` has no build section, or a self-closing one
- **THEN** Sentinel creates the build and plugins sections inside the project and adds the generated plugin there

#### Scenario: Comments mention closing tags
- **WHEN** a comment in `pom.xml` contains text such as `</plugins>`
- **THEN** the comment does not affect where the declaration is inserted

### Requirement: Edited pom.xml is validated before it is written

Before writing `pom.xml`, Sentinel SHALL check the complete edited content. It SHALL be well-formed XML. Each added declaration SHALL be in its project-level section. Each element directly inside an added plugin or dependency SHALL be one that Maven allows there. If any check fails, Sentinel SHALL NOT write `pom.xml`, SHALL name the tool whose declaration failed, and SHALL fail initialization with the existing rollback behavior.

#### Scenario: Generated declaration is valid
- **WHEN** every check passes for the edited `pom.xml`
- **THEN** Sentinel writes it and reports the configured tools as before

#### Scenario: Generated plugin contains an element Maven does not allow
- **WHEN** an added plugin declaration contains an element that Maven does not allow directly inside a plugin, such as a configuration value outside `<configuration>`
- **THEN** Sentinel leaves `pom.xml` unchanged, reports the affected tool, and rolls back other changes made by this initialization

#### Scenario: Edited content is not well-formed
- **WHEN** the edited `pom.xml` content is not well-formed XML
- **THEN** Sentinel leaves `pom.xml` unchanged and reports that it could not safely update the file

#### Scenario: Validation does not run external processes or fetch documents
- **WHEN** Sentinel validates the edited `pom.xml`
- **THEN** it runs no build or other external process, and it neither resolves document type definitions nor fetches external entities

### Requirement: Managed plugin version updates are scoped to the matching declaration

When the user approves a compatibility version update for a managed plugin, Sentinel SHALL change only the version of that plugin's project-level declaration. If that declaration or its version cannot be found exactly, Sentinel SHALL refuse the update and SHALL NOT change any other text.

#### Scenario: Another plugin declares the old version
- **WHEN** a plugin declared earlier or later in `pom.xml` uses the same version string as the managed plugin
- **THEN** only the managed plugin's version changes

#### Scenario: Managed plugin declaration has no explicit version
- **WHEN** the managed plugin's project-level declaration has no version element matching the inspected version
- **THEN** Sentinel refuses the update with an actionable error and leaves `pom.xml` unchanged

#### Scenario: Managed plugin is declared only in a profile
- **WHEN** the managed plugin with the inspected version appears only inside a profile
- **THEN** Sentinel leaves `pom.xml` unchanged and reports a warning that the selected gate was not reconfigured, as it does for a declined update
