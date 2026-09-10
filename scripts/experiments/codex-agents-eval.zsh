#!/bin/zsh
set -euo pipefail

if (( $# != 5 )); then
  print -u2 'usage: codex-agents-eval.zsh VARIANT SCENARIO WORKTREE PROMPT OUTPUT_DIR'
  exit 64
fi

variant_name="$1"
scenario_name="$2"
worktree_path="$3"
prompt_text="$4"
output_dir="$5"

test -d "$worktree_path"
mkdir -p "$output_dir"

json_path="$output_dir/${variant_name}-${scenario_name}.jsonl"
answer_path="$output_dir/${variant_name}-${scenario_name}-answer.txt"
start_seconds=$SECONDS

codex exec --json --model gpt-5.6-sol \
  --config 'model_reasoning_effort="high"' \
  --sandbox read-only \
  --cd "$worktree_path" \
  "$prompt_text" </dev/null | tee "$json_path" >/dev/null

elapsed_seconds=$((SECONDS - start_seconds))
thread_id=$(jq -r 'select(.type == "thread.started") | .thread_id' "$json_path" | sed -n '1p')
test -n "$thread_id"
rollout_file=$(find ~/.codex/sessions -type f -name "rollout-*${thread_id}.jsonl" -print | sed -n '1p')
test -f "$rollout_file"

first_usage=$(jq -c 'select(.type == "event_msg" and .payload.type == "token_count") | .payload.info.last_token_usage' "$rollout_file" | sed -n '1p')
final_usage=$(jq -c 'select(.type == "event_msg" and .payload.type == "token_count") | .payload.info.total_token_usage' "$rollout_file" | tail -n 1)
tool_calls=$(jq -s '[.[] | select(.type == "response_item" and .payload.type == "custom_tool_call")] | length' "$rollout_file")
jq -r 'select(.type == "item.completed" and .item.type == "agent_message") | .item.text' "$json_path" > "$answer_path"

test -n "$first_usage"
test -n "$final_usage"
test -s "$answer_path"

printf 'variant=%s\nscenario=%s\nthread_id=%s\nfirst_usage=%s\nfinal_usage=%s\ntool_calls=%s\nelapsed_seconds=%s\njson_path=%s\nanswer_path=%s\nrollout_file=%s\n' \
  "$variant_name" "$scenario_name" "$thread_id" "$first_usage" "$final_usage" \
  "$tool_calls" "$elapsed_seconds" "$json_path" "$answer_path" "$rollout_file"
