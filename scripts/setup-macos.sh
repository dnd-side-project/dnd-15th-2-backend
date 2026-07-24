#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTALL=false
INSTALL_AGENTS=false
INSTALL_HOOKS=false

usage() {
  cat <<'EOF'
Usage:
  ./scripts/setup-macos.sh
  ./scripts/setup-macos.sh --install [--install-agents] [--install-hooks]

Default mode only checks the machine. --install runs Brewfile installation.
--install-agents additionally installs the official Claude Code and Codex npm
packages. --install-hooks runs npm ci and installs repository-local Husky hooks.
Authentication is always interactive and is never performed here.
EOF
}

for argument in "$@"; do
  case "$argument" in
    --install) INSTALL=true ;;
    --install-agents) INSTALL_AGENTS=true ;;
    --install-hooks) INSTALL_HOOKS=true ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown argument: $argument" >&2; usage; exit 2 ;;
  esac
done

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This setup is intended for macOS." >&2
  exit 1
fi

if ! xcode-select -p >/dev/null 2>&1; then
  echo "[missing] Xcode Command Line Tools"
  echo "Install manually with: xcode-select --install"
  exit 1
fi

if ! command -v brew >/dev/null 2>&1; then
  echo "[missing] Homebrew"
  echo "Install from https://brew.sh and rerun this script."
  exit 1
fi

if "$INSTALL"; then
  echo "[install] Homebrew dependencies from Brewfile"
  brew bundle --file "$ROOT/Brewfile"
fi

if "$INSTALL_AGENTS"; then
  if ! command -v npm >/dev/null 2>&1; then
    echo "npm is required before installing coding agents." >&2
    exit 1
  fi
  echo "[install] Claude Code and Codex CLI"
  npm install -g @anthropic-ai/claude-code @openai/codex
fi

if "$INSTALL_HOOKS"; then
  if ! command -v npm >/dev/null 2>&1; then
    echo "npm is required before installing repository hooks." >&2
    exit 1
  fi
  echo "[install] Repository dependencies and Husky hooks"
  (
    cd "$ROOT"
    npm ci
  )
fi

TOOLS=(git gh java python3 node npm jq shellcheck aws terraform)
missing=0
for tool in "${TOOLS[@]}"; do
  if command -v "$tool" >/dev/null 2>&1; then
    printf '[ok]      %s\n' "$tool"
  else
    printf '[missing] %s\n' "$tool"
    missing=1
  fi
done

for agent in claude codex; do
  if command -v "$agent" >/dev/null 2>&1; then
    printf '[ok]      %s\n' "$agent"
  else
    printf '[optional] %s (use --install --install-agents)\n' "$agent"
  fi
done

if command -v gh >/dev/null 2>&1; then
  if gh auth status >/dev/null 2>&1; then
    echo "[ok]      GitHub CLI authentication"
  else
    echo "[action]  Run: gh auth login"
  fi
fi

if command -v claude >/dev/null 2>&1; then
  echo "[action]  Run interactively once: claude doctor"
fi

if command -v codex >/dev/null 2>&1; then
  echo "[action]  Run interactively once: codex --login"
fi

if (( missing != 0 )); then
  echo "Some required tools are missing. Rerun with --install." >&2
  exit 1
fi

echo "Machine check passed."
echo "Install repository shortcuts with:"
echo "  ./scripts/install-shortcuts.zsh --install"
echo "Install repository hooks with:"
echo "  ./scripts/setup-macos.sh --install-hooks"
