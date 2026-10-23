#!/bin/sh
# Ward installer for Linux, macOS, and Git Bash on Windows (PowerShell users: install.ps1):
# downloads a native release archive, verifies its SHA-256 checksum, and installs it as `ward`
# together with its LICENSE and NOTICE.
#
#   curl -fsSL https://raw.githubusercontent.com/RicardoRB/ward-ai/main/install.sh | sh
#
# Environment variables:
#   WARD_VERSION      Release tag to install, e.g. v0.0.1 (default: latest)
#   WARD_INSTALL_DIR  Target directory (default: $HOME/.local/bin)
#   WARD_DOC_DIR      LICENSE and NOTICE directory
#                         (default: <install dir parent>/share/doc/ward)
#   WARD_BASE_URL     Override the download base URL (mirrors, testing)
set -eu

REPO="RicardoRB/ward-ai"
VERSION="${WARD_VERSION:-latest}"
INSTALL_DIR="${WARD_INSTALL_DIR:-$HOME/.local/bin}"
DOC_DIR="${WARD_DOC_DIR:-$(dirname "$INSTALL_DIR")/share/doc/ward}"

info() { printf 'ward-install: %s\n' "$*"; }
fail() {
  printf 'ward-install: error: %s\n' "$*" >&2
  exit 1
}

# Sets asset, archive, and exe for this machine.
detect_platform() {
  os=$(uname -s)
  arch=$(uname -m)
  case "$os" in
    Linux) os=linux ;;
    Darwin) os=macos ;;
    MINGW* | MSYS* | CYGWIN*) os=windows ;;
    *) fail "unsupported operating system: $os" ;;
  esac
  case "$arch" in
    x86_64 | amd64) arch=x86_64 ;;
    arm64 | aarch64) arch=aarch64 ;;
    *) fail "unsupported architecture: $arch" ;;
  esac
  if [ "$os" = linux ] && [ "$arch" = aarch64 ]; then
    fail "no Linux aarch64 binary is published yet; build from source: https://github.com/$REPO#development"
  fi
  if [ "$os" = windows ] && [ "$arch" = aarch64 ]; then
    info "no Windows ARM64 binary is published; installing x86_64, which runs under emulation"
    arch=x86_64
  fi
  asset="ward-$os-$arch"
  if [ "$os" = windows ]; then
    archive="$asset.zip"
    exe=ward.exe
  else
    archive="$asset.tar.gz"
    exe=ward
  fi
}

download() {
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL --retry 3 -o "$2" "$1"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$2" "$1"
  else
    fail "curl or wget is required"
  fi
}

# extract <archive> <directory>
extract() {
  case "$1" in
    *.zip)
      command -v unzip >/dev/null 2>&1 || fail "unzip is required to extract $(basename "$1")"
      unzip -q "$1" -d "$2"
      ;;
    *) tar -xzf "$1" -C "$2" ;;
  esac
}

# verify <file> <checksum-file>
verify() {
  expected=$(cut -d ' ' -f 1 "$2")
  actual=$(sha256 "$1")
  [ -n "$expected" ] && [ "$expected" = "$actual" ] \
    || fail "checksum mismatch for $(basename "$1") (expected $expected, got $actual)"
}

sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d ' ' -f 1
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | cut -d ' ' -f 1
  else
    fail "sha256sum or shasum is required to verify the download"
  fi
}

main() {
  detect_platform

  if [ -n "${WARD_BASE_URL:-}" ]; then
    base_url="$WARD_BASE_URL"
  elif [ "$VERSION" = latest ]; then
    base_url="https://github.com/$REPO/releases/latest/download"
  else
    base_url="https://github.com/$REPO/releases/download/$VERSION"
  fi

  tmp=$(mktemp -d)
  trap 'rm -rf "$tmp"' EXIT INT TERM

  info "downloading $asset ($VERSION)"
  download "$base_url/$archive" "$tmp/$archive" || fail "download failed: $base_url/$archive"
  download "$base_url/$archive.sha256" "$tmp/$archive.sha256" \
    || fail "checksum download failed: $base_url/$archive.sha256"
  verify "$tmp/$archive" "$tmp/$archive.sha256"
  info "checksum verified"

  mkdir "$tmp/extract"
  extract "$tmp/$archive" "$tmp/extract" || fail "cannot extract $archive"
  for file in "$exe" LICENSE NOTICE; do
    [ -f "$tmp/extract/$file" ] || fail "$archive does not contain $file"
  done

  mkdir -p "$INSTALL_DIR" || fail "cannot create $INSTALL_DIR; set WARD_INSTALL_DIR"
  [ -w "$INSTALL_DIR" ] || fail "$INSTALL_DIR is not writable; set WARD_INSTALL_DIR or re-run with sudo"
  chmod +x "$tmp/extract/$exe"
  mv "$tmp/extract/$exe" "$INSTALL_DIR/$exe"

  if mkdir -p "$DOC_DIR" 2>/dev/null && cp "$tmp/extract/LICENSE" "$tmp/extract/NOTICE" "$DOC_DIR/"; then
    info "installed LICENSE and NOTICE to $DOC_DIR"
  else
    info "could not write LICENSE and NOTICE to $DOC_DIR; set WARD_DOC_DIR"
  fi
  info "installed $("$INSTALL_DIR/$exe" --version 2>/dev/null || echo ward) to $INSTALL_DIR/$exe"

  case ":$PATH:" in
    *":$INSTALL_DIR:"*) ;;
    *)
      info "$INSTALL_DIR is not on your PATH; add this to your shell profile:"
      printf '\n    export PATH="%s:$PATH"\n\n' "$INSTALL_DIR"
      ;;
  esac

  info "next: install the agent skills, then run /ward-init in your coding agent:"
  printf '\n    npx skills add %s --skill ward-init ward-tune\n\n' "$REPO"
}

main "$@"
