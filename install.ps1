# Ward installer for Windows: downloads the native release archive, verifies its SHA-256
# checksum, installs ward.exe together with its LICENSE and NOTICE, and adds it to the user PATH.
#
#   irm https://raw.githubusercontent.com/RicardoRB/ward-ai/main/install.ps1 | iex
#
# Environment variables:
#   WARD_VERSION      Release tag to install, e.g. v0.0.1 (default: latest)
#   WARD_INSTALL_DIR  Target directory (default: %LOCALAPPDATA%\Programs\ward\bin)
#   WARD_DOC_DIR      LICENSE and NOTICE directory (default: <install dir parent>)
#   WARD_BASE_URL     Override the download base URL (mirrors, testing)
#
# Requires Windows PowerShell 5.1 or PowerShell 7.

# A script block keeps `irm | iex` from leaking variables or settings into the caller's session,
# and errors are thrown rather than calling `exit`, which would close that session.
& {
  $ErrorActionPreference = 'Stop'
  $ProgressPreference = 'SilentlyContinue'
  [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

  $repo = 'RicardoRB/ward-ai'
  $version = if ($env:WARD_VERSION) { $env:WARD_VERSION } else { 'latest' }
  $installDir = if ($env:WARD_INSTALL_DIR) { $env:WARD_INSTALL_DIR } else { Join-Path $env:LOCALAPPDATA 'Programs\ward\bin' }
  $docDir = if ($env:WARD_DOC_DIR) { $env:WARD_DOC_DIR } else { Split-Path $installDir -Parent }

  function Write-Info([string] $message) { Write-Host "ward-install: $message" }

  # PROCESSOR_ARCHITEW6432 holds the real architecture when 32-bit PowerShell runs on 64-bit Windows.
  $arch = if ($env:PROCESSOR_ARCHITEW6432) { $env:PROCESSOR_ARCHITEW6432 } else { $env:PROCESSOR_ARCHITECTURE }
  switch ($arch) {
    'AMD64' { }
    'ARM64' { Write-Info 'no Windows ARM64 binary is published; installing x86_64, which runs under emulation' }
    default { throw "ward-install: error: unsupported architecture: $arch" }
  }

  $archive = 'ward-windows-x86_64.zip'
  $baseUrl = if ($env:WARD_BASE_URL) {
    $env:WARD_BASE_URL
  } elseif ($version -eq 'latest') {
    "https://github.com/$repo/releases/latest/download"
  } else {
    "https://github.com/$repo/releases/download/$version"
  }

  $tmp = Join-Path ([IO.Path]::GetTempPath()) ("ward-install-" + [Guid]::NewGuid())
  New-Item -ItemType Directory -Path $tmp | Out-Null
  try {
    Write-Info "downloading ward-windows-x86_64 ($version)"
    $archivePath = Join-Path $tmp $archive
    Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/$archive" -OutFile $archivePath
    Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/$archive.sha256" -OutFile "$archivePath.sha256"

    $expected = ((Get-Content "$archivePath.sha256" -Raw).Trim() -split '\s+')[0]
    $actual = (Get-FileHash $archivePath -Algorithm SHA256).Hash
    if (-not $expected -or $expected -ne $actual) {
      throw "ward-install: error: checksum mismatch for $archive (expected $expected, got $($actual.ToLower()))"
    }
    Write-Info 'checksum verified'

    $extract = Join-Path $tmp 'extract'
    Expand-Archive -Path $archivePath -DestinationPath $extract
    foreach ($file in 'ward.exe', 'LICENSE', 'NOTICE') {
      if (-not (Test-Path (Join-Path $extract $file) -PathType Leaf)) {
        throw "ward-install: error: $archive does not contain $file"
      }
    }

    New-Item -ItemType Directory -Force -Path $installDir | Out-Null
    $exe = Join-Path $installDir 'ward.exe'
    Move-Item -Force (Join-Path $extract 'ward.exe') $exe

    try {
      New-Item -ItemType Directory -Force -Path $docDir | Out-Null
      Copy-Item -Force (Join-Path $extract 'LICENSE'), (Join-Path $extract 'NOTICE') $docDir
      Write-Info "installed LICENSE and NOTICE to $docDir"
    } catch {
      Write-Info "could not write LICENSE and NOTICE to $docDir; set WARD_DOC_DIR"
    }
  } finally {
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
  }

  $installed = try { & $exe --version 2>$null } catch { 'ward' }
  Write-Info "installed $installed to $exe"

  $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
  $entries = if ($userPath) { $userPath -split ';' | Where-Object { $_ } } else { @() }
  if ($entries -notcontains $installDir) {
    [Environment]::SetEnvironmentVariable('Path', (@($entries) + $installDir) -join ';', 'User')
    Write-Info "added $installDir to your user PATH; open a new terminal to use ward elsewhere"
  }
  if (($env:Path -split ';') -notcontains $installDir) {
    $env:Path = "$env:Path;$installDir"
  }

  Write-Info 'next: install the agent skills, then run /ward-init in your coding agent:'
  Write-Host "`n    npx skills add $repo --skill ward-init ward-tune`n"
}
