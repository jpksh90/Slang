#!/usr/bin/env bash
set -euo pipefail

# approve_snapshots.sh
# Find files named *.received.* (ApprovalTests pattern) and optionally rename them to *.approved.*
# Usage:
#   ./scripts/approve_snapshots.sh         # dry run - prints changes that would happen
#   ./scripts/approve_snapshots.sh --commit  # perform git mv and commit the changes
#   ./scripts/approve_snapshots.sh --help  # show help

SHOW_HELP=0
DO_COMMIT=0

for arg in "$@"; do
  case "$arg" in
    --commit) DO_COMMIT=1 ;;
    --help|-h) SHOW_HELP=1 ;;
    *) echo "Unknown arg: $arg" ; exit 1 ;;
  esac
done

if [ "$SHOW_HELP" -eq 1 ]; then
  sed -n '1,200p' "$0"
  exit 0
fi

# Find received files: patterns like *.received.txt or *.received.md or *.received.json
RECEIVED_FILES=()
while IFS= read -r -d '' file; do
  RECEIVED_FILES+=("$file")
done < <(find . -type f -name "*.received.*" -print0)

if [ ${#RECEIVED_FILES[@]} -eq 0 ]; then
  echo "No received snapshot files found. Nothing to do."
  exit 0
fi

echo "Found ${#RECEIVED_FILES[@]} received file(s):"
for f in "${RECEIVED_FILES[@]}"; do
  echo "  $f"
done

actions=()
for f in "${RECEIVED_FILES[@]}"; do
  # compute approved filename: replace .received. with .approved.
  approved="${f//.received./.approved.}"
  actions+=("$f -> $approved")
done

echo
if [ "$DO_COMMIT" -eq 0 ]; then
  echo "DRY RUN: the following rename operations would be performed:"
  for a in "${actions[@]}"; do
    echo "  $a"
  done
  echo
  echo "Run with --commit to actually perform git mv and commit the changes."
  exit 0
fi

# If we get here, perform commit
# Ensure working tree is clean
if [ -n "$(git status --porcelain)" ]; then
  echo "Working tree is not clean. Please commit or stash your changes before running with --commit." >&2
  git status --porcelain
  exit 1
fi

# Perform git mv operations
for f in "${RECEIVED_FILES[@]}"; do
  approved="${f//.received./.approved.}"
  echo "git mv '$f' '$approved'"
  git mv -- "$f" "$approved"
done

# Commit
msg="chore(test): approve snapshots ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
git add -A
git commit -m "$msg"

echo "Committed snapshot approvals."

exit 0
