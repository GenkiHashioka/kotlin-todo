#!/usr/bin/env bash
# SessionStart フック。両クローンの現在ブランチと、対応する引き継ぎファイルを Claude に渡す。
# additionalContext は Claude のコンテキストに入り、systemMessage は開発者の画面に 1 行表示される。

project_dir="${CLAUDE_PROJECT_DIR:-$(pwd)}"
windows_clone="${KOTLIN_TODO_WINDOWS_CLONE:-/mnt/c/Users/gensh/projects/kotlin-todo}"
handoff_dir="$project_dir/.claude/handoff"

context="## 作業中のブランチ"
branches=()
for clone in "$project_dir" "$windows_clone"; do
  [ -d "$clone/.git" ] || continue
  branch="$(git -C "$clone" branch --show-current 2>/dev/null)"
  context+=$'\n'"- $clone: ${branch:-(detached)}"
  if [ -n "$branch" ] && [ "$branch" != "main" ]; then
    branches+=("$branch")
  fi
done

loaded=()
for branch in "${branches[@]}"; do
  handoff_file="$handoff_dir/${branch//\//-}.md"
  [ -f "$handoff_file" ] || continue
  loaded+=("$branch")
  context+=$'\n\n'"## 引き継ぎ: $branch（.claude/handoff/${branch//\//-}.md）"$'\n'"$(cat "$handoff_file")"
done

if [ "${#loaded[@]}" -eq 0 ]; then
  context+=$'\n\n'"引き継ぎファイルはない。新しい Issue なら、Issue と design-note を読んでから始める。"
  message="引き継ぎファイルなし"
else
  message="引き継ぎを読み込みました: ${loaded[*]}"
fi

CONTEXT="$context" MESSAGE="$message" python3 -c '
import json, os
print(json.dumps({
    "systemMessage": os.environ["MESSAGE"],
    "hookSpecificOutput": {
        "hookEventName": "SessionStart",
        "additionalContext": os.environ["CONTEXT"],
    },
}, ensure_ascii=False))
'
