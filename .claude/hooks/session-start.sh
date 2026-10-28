#!/usr/bin/env bash
# SessionStart フック。両クローンの現在ブランチと、対応する引き継ぎファイルを Claude に渡す。
# 標準出力がそのまま Claude のコンテキストに入る。

project_dir="${CLAUDE_PROJECT_DIR:-$(pwd)}"
windows_clone="${KOTLIN_TODO_WINDOWS_CLONE:-/mnt/c/Users/gensh/projects/kotlin-todo}"
handoff_dir="$project_dir/.claude/handoff"

echo "## 作業中のブランチ"
branches=()
for clone in "$project_dir" "$windows_clone"; do
  [ -d "$clone/.git" ] || continue
  branch="$(git -C "$clone" branch --show-current 2>/dev/null)"
  echo "- $clone: ${branch:-(detached)}"
  if [ -n "$branch" ] && [ "$branch" != "main" ]; then
    branches+=("$branch")
  fi
done

found=0
for branch in "${branches[@]}"; do
  handoff_file="$handoff_dir/${branch//\//-}.md"
  [ -f "$handoff_file" ] || continue
  found=1
  echo
  echo "## 引き継ぎ: $branch（.claude/handoff/${branch//\//-}.md）"
  cat "$handoff_file"
done

if [ "$found" -eq 0 ]; then
  echo
  echo "引き継ぎファイルはない。新しい Issue なら、Issue と design-note を読んでから始める。"
fi
