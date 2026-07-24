# Repository-local helper for developers who prefer manual dotfile management.
# Add `source /absolute/path/to/miri/shell/harness.zsh` to ~/.zshrc.

typeset -g MIRI_HARNESS_ROOT="${${(%):-%N}:A:h:h}"
fpath=("${MIRI_HARNESS_ROOT}/completions" $fpath)
autoload -Uz compinit
compinit

h() {
  "${MIRI_HARNESS_ROOT}/harness" "$@"
}

alias hd='h doctor'
alias hs='h status'
alias hctx='h context'
alias hc='h check'
alias hpr='h pr-ready --project-tests'
alias hcheat='h cheatsheet'

htp() {
  h test-plan --id "$1"
}

htr() {
  h test-run --id "$1"
}

hid() {
  h infra-design --id "$1"
}
