# interactive-init-setup Specification

## Purpose
This capability turns project initialization into a guided setup that lets users choose an agent integration and quality gate while clearly handling missing external tooling without overwriting existing project configuration.

## Requirements

### Requirement: Interactive initialization selections

When creating a new configuration, `sentinel init` SHALL present a console selection for a supported agent integration and a supported quality gate, including an explicit no-integration choice.

#### Scenario: Create configuration from interactive selections
- **WHEN** a supported project runs `sentinel init` without selection options and chooses an agent integration and quality gate
- **THEN** Sentinel creates `sentinel.toml` using the selected gate and installs the selected ownership-safe integration, or skips integration when no integration is selected

#### Scenario: Use explicit selections for automation
- **WHEN** a supported project runs `sentinel init` with explicit integration and gate selections
- **THEN** Sentinel performs the equivalent setup without prompting for those selections

#### Scenario: Reject an invalid selection
- **WHEN** the user enters an integration or gate option that is not supported
- **THEN** Sentinel reports the available choices, exits with an error, and does not create or modify initialization artifacts

### Requirement: Quality-gate availability reporting

The initialization flow SHALL show whether the selected gate's required executable or project capability is available and SHALL configure the selected gate even when an external tool is unavailable; initialization MUST NOT install external tools automatically.

#### Scenario: Selected gate is available
- **WHEN** the selected gate's required project wrapper, executable, or built-in capability is detected
- **THEN** Sentinel reports it as available and writes the selected enabled gate configuration

#### Scenario: Selected external tool is unavailable
- **WHEN** the selected gate requires an executable that is not detected
- **THEN** Sentinel reports the gate as unavailable with remediation information, writes the selected gate configuration, and leaves the later `sentinel check`/`doctor` result responsible for reporting execution failure

#### Scenario: Init does not run the selected gate
- **WHEN** initialization completes for any selected gate
- **THEN** Sentinel only detects availability and writes configuration; the quality gate runs when the user later invokes `sentinel check` or an agent guard invokes it

### Requirement: Integration lifecycle and configuration safety

Initialization SHALL use the existing integration ownership and conflict rules, SHALL not overwrite an existing `sentinel.toml`, and SHALL avoid leaving a newly created configuration with an enabled integration that was not successfully installed.

#### Scenario: Existing configuration is present
- **WHEN** `sentinel init` finds an existing `sentinel.toml`
- **THEN** Sentinel preserves it, does not prompt for setup or modify integrations, and reports that no initialization changes were made

#### Scenario: Selected integration conflicts
- **WHEN** the selected integration target is user-owned or incompatible
- **THEN** Sentinel reports the conflict, preserves the user-owned content, and does not claim that the integration was enabled by initialization

#### Scenario: No integration is selected
- **WHEN** the user chooses the no-integration option
- **THEN** Sentinel creates only the selected quality-gate configuration and reports that no agent integration was installed

### Requirement: Non-interactive and cancellation behavior

The initialization command SHALL support explicit selection options for automation and SHALL fail safely on invalid or end-of-file input without creating partial configuration or integration artifacts.

#### Scenario: Input ends before setup completes
- **WHEN** interactive input reaches end-of-file before all required selections are made
- **THEN** Sentinel exits with an actionable error and leaves the project unchanged

#### Scenario: Existing non-interactive workflow remains usable
- **WHEN** automation supplies all required initialization selections explicitly
- **THEN** Sentinel performs initialization without requiring a TTY or interactive input

### Requirement: Multi-select agent integrations

The interactive integration selector SHALL visibly mark the highlighted option with a `>` cursor, allow multiple integrations to be selected before confirmation, use checkbox-style state where Space toggles the highlighted option and Enter confirms. Non-interactive input SHALL support space-separated option numbers, while keeping the no-integration choice mutually exclusive with agent integrations.

#### Scenario: Select multiple integrations
- **WHEN** the user selects OpenCode and Claude Code in the integration selector and confirms
- **THEN** Sentinel installs both ownership-safe integrations and reports each result

#### Scenario: Select no integration
- **WHEN** the user selects only the no-integration option and confirms
- **THEN** Sentinel installs no agent integrations

#### Scenario: Reject a mixed no-integration selection
- **WHEN** the user selects no integration together with one or more agent integrations
- **THEN** Sentinel reports that no integration is mutually exclusive and makes no initialization changes

### Requirement: Multi-select quality gates

The interactive quality-gate selector SHALL use the same visible `>` cursor and checkbox interaction as integration selection, allowing multiple gates to be toggled with Space and confirmed with Enter. Non-interactive input SHALL support space-separated gate numbers.

#### Scenario: Select multiple quality gates
- **WHEN** the user selects tests and compile in the quality-gate selector and confirms
- **THEN** Sentinel writes both enabled quality-gate sections and reports availability for both

#### Scenario: Confirm quality-gate selection
- **WHEN** the user moves the `>` cursor, toggles one or more gates with Space, and presses Enter
- **THEN** only the checked gates are configured and the highlighted cursor is not treated as a selection unless it is checked

#### Scenario: Reject an empty quality-gate selection
- **WHEN** the user confirms without selecting any quality gate
- **THEN** Sentinel reports an actionable error and makes no initialization changes

### Requirement: Maven tool configuration generation

When selected quality gates require Maven plugins or test dependencies, initialization SHALL add pinned minimal Maven configuration to `pom.xml` only when the corresponding artifact is absent, preserve existing project configuration, and report the generated changes.

#### Scenario: Add missing Maven tool configuration
- **WHEN** the user selects Checkstyle, SpotBugs, Sonar, or ArchUnit-based gates and the required Maven plugin/dependency is absent
- **THEN** Sentinel adds the pinned minimal plugin or dependency configuration to `pom.xml` before writing `sentinel.toml`

#### Scenario: Preserve existing Maven tool configuration
- **WHEN** a selected gate's Maven plugin or dependency is already declared in `pom.xml`
- **THEN** Sentinel does not duplicate or overwrite that declaration

#### Scenario: Maven configuration changes are reported
- **WHEN** initialization adds one or more Maven tool declarations
- **THEN** Sentinel reports that `pom.xml` changed and identifies the configured tools

#### Scenario: Setup failure rolls back generated Maven changes
- **WHEN** integration or configuration creation fails after Sentinel has added Maven declarations
- **THEN** Sentinel restores the original `pom.xml` content and preserves any user-owned conflicting files

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

### Requirement: ArchUnit test generation and architecture style

When `architecture` or `archunit` is selected, initialization SHALL ensure an ArchUnit test exists and SHALL never overwrite an existing user-owned test. Interactive initialization SHALL ask which supported architecture style the generated test should enforce.

#### Scenario: Generate an ArchUnit test for a selected style
- **WHEN** the user selects an architecture gate and chooses Layered, Hexagonal, or Clean architecture
- **THEN** Sentinel adds the ArchUnit test dependency, generates `ArchitectureTest.java` using the selected style, and configures the gate to run only that test

#### Scenario: Preserve an existing ArchUnit test
- **WHEN** `ArchitectureTest.java` already exists
- **THEN** Sentinel preserves its contents and reports that the existing test was not replaced

#### Scenario: Select architecture style explicitly
- **WHEN** automation supplies `--architecture layered`, `--architecture hexagonal`, or `--architecture clean`
- **THEN** Sentinel performs initialization without prompting for the architecture style

#### Scenario: Reject an unsupported architecture style
- **WHEN** an unsupported architecture style is supplied
- **THEN** Sentinel reports the supported styles and makes no initialization changes
