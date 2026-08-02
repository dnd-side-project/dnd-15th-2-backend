#!/usr/bin/env zsh
set -euo pipefail

ROOT="${0:A:h:h}"
TARGET_DIR="${HOME}/.config/qello-harness"
TARGET_FILE="${TARGET_DIR}/env.zsh"
ZSHRC="${HOME}/.zshrc"
BEGIN_MARKER="# >>> qello harness >>>"
END_MARKER="# <<< qello harness <<<"

if [[ "${1:-}" != "--install" ]]; then
  cat <<EOF
Dry run only. This command will:
  1. create ${TARGET_FILE}
  2. back up ${ZSHRC} when it exists
  3. add one managed source block to ${ZSHRC}

Run explicitly:
  ./scripts/install-shortcuts.zsh --install
EOF
  exit 0
fi

mkdir -p "$TARGET_DIR"
cat > "$TARGET_FILE" <<EOF
export QELLO_HARNESS_ROOT="${ROOT}"
fpath=("\${QELLO_HARNESS_ROOT}/completions" \$fpath)
autoload -Uz compinit
compinit
h() {
  "\${QELLO_HARNESS_ROOT}/harness" "\$@"
}
alias hd='h doctor'
alias hs='h status'
alias hc='h check'
alias hpr='h pr-ready --project-tests'
alias hcheat='h cheatsheet'
htp() {
  h test-plan --id "\$1"
}
htr() {
  h test-run --id "\$1"
}
hid() {
  h infra-design --id "\$1"
}
EOF

touch "$ZSHRC"
if grep -Fq "$BEGIN_MARKER" "$ZSHRC"; then
  echo "Managed block already exists in ${ZSHRC}."
else
  backup="${ZSHRC}.qello-harness.$(date +%Y%m%d%H%M%S).bak"
  cp "$ZSHRC" "$backup"
  {
    echo
    echo "$BEGIN_MARKER"
    echo "source \"${TARGET_FILE}\""
    echo "$END_MARKER"
  } >> "$ZSHRC"
  echo "Backup created: ${backup}"
fi

echo "Shortcuts installed. Reload with: exec zsh"
