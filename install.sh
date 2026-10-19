#!/bin/sh
# Sentinel installer: downloads a native release archive, verifies its SHA-256
# checksum, and installs it as `sentinel` together with its LICENSE and NOTICE.
#
#   curl -fsSL https://raw.githubusercontent.com/RicardoRB/sentinel-ai/main/install.sh | sh
#
# Environment variables:
#   SENTINEL_VERSION      Release tag to install, e.g. v0.0.1 (default: latest)
#   SENTINEL_INSTALL_DIR  Target directory (default: $HOME/.local/bin)
#   SENTINEL_DOC_DIR      LICENSE and NOTICE directory
#                         (default: <install dir parent>/share/doc/sentinel)
#   SENTINEL_BASE_URL     Override the download base URL (mirrors, testing)
set -eu

REPO="RicardoRB/sentinel-ai"
VERSION="${SENTINEL_VERSION:-latest}"
INSTALL_DIR="${SENTINEL_INSTALL_DIR:-$HOME/.local/bin}"
DOC_DIR="${SENTINEL_DOC_DIR:-$(dirname "$INSTALL_DIR")/share/doc/sentinel}"

info() { printf 'sentinel-install: %s\n' "$*"; }
fail() {
  printf 'sentinel-install: error: %s\n' "$*" >&2
  exit 1
}

detect_platform() {
  os=$(uname -s)
  arch=$(uname -m)
  case "$os" in
    Linux) os=linux ;;
    Darwin) os=macos ;;
    MINGW* | MSYS* | CYGWIN*)
      fail "Windows is not supported by this script; download sentinel-windows-x86_64.exe from https://github.com/$REPO/releases" ;;
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
  printf 'sentinel-%s-%s' "$os" "$arch"
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
  asset=$(detect_platform)

  if [ -n "${SENTINEL_BASE_URL:-}" ]; then
    base_url="$SENTINEL_BASE_URL"
  elif [ "$VERSION" = latest ]; then
    base_url="https://github.com/$REPO/releases/latest/download"
  else
    base_url="https://github.com/$REPO/releases/download/$VERSION"
  fi

  tmp=$(mktemp -d)
  trap 'rm -rf "$tmp"' EXIT INT TERM

  info "downloading $asset ($VERSION)"
  archive="$asset.tar.gz"
  download "$base_url/$archive" "$tmp/$archive" || fail "download failed: $base_url/$archive"
  download "$base_url/$archive.sha256" "$tmp/$archive.sha256" \
    || fail "checksum download failed: $base_url/$archive.sha256"
  verify "$tmp/$archive" "$tmp/$archive.sha256"
  info "checksum verified"

  mkdir "$tmp/extract"
  tar -xzf "$tmp/$archive" -C "$tmp/extract" || fail "cannot extract $archive"
  for file in sentinel LICENSE NOTICE; do
    [ -f "$tmp/extract/$file" ] || fail "$archive does not contain $file"
  done

  mkdir -p "$INSTALL_DIR" || fail "cannot create $INSTALL_DIR; set SENTINEL_INSTALL_DIR"
  [ -w "$INSTALL_DIR" ] || fail "$INSTALL_DIR is not writable; set SENTINEL_INSTALL_DIR or re-run with sudo"
  chmod +x "$tmp/extract/sentinel"
  mv "$tmp/extract/sentinel" "$INSTALL_DIR/sentinel"

  if mkdir -p "$DOC_DIR" 2>/dev/null && cp "$tmp/extract/LICENSE" "$tmp/extract/NOTICE" "$DOC_DIR/"; then
    info "installed LICENSE and NOTICE to $DOC_DIR"
  else
    info "could not write LICENSE and NOTICE to $DOC_DIR; set SENTINEL_DOC_DIR"
  fi
  info "installed $("$INSTALL_DIR/sentinel" --version 2>/dev/null || echo sentinel) to $INSTALL_DIR/sentinel"

  case ":$PATH:" in
    *":$INSTALL_DIR:"*) ;;
    *)
      info "$INSTALL_DIR is not on your PATH; add this to your shell profile:"
      printf '\n    export PATH="%s:$PATH"\n\n' "$INSTALL_DIR"
      ;;
  esac

  info "next: install the agent skills, then run /sentinel-init in your coding agent:"
  printf '\n    npx skills add %s --skill sentinel-init sentinel-tune\n\n' "$REPO"
}

main "$@"
