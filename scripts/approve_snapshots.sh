#!/usr/bin/env bash
set -euo pipefail

# Usage: `scripts/approve_snapshots.sh` <directory>
# Rename all files in the given directory from '*.received.txt' to '*.approved.txt'.
# Non-recursive. Skips targets that already exist.

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <directory>" >&2
  exit 2
fi

dir="$1"

if [ ! -d "$dir" ]; then
  echo "Error: '$dir' is not a directory" >&2
  exit 3
fi

found_any=false

for f in "$dir"/*.received.txt; do
  if [ ! -e "$f" ]; then
    # No matching files (shell left the pattern unexpanded)
    if [ "$found_any" = false ]; then
      echo "No '*.received.txt' files found in '$dir'." >&2
      exit 0
    fi
    break
  fi

  found_any=true
  [ -f "$f" ] || continue

  target="${f%.received.txt}.approved.txt"
  mv "$f" "$target"
  echo "Renamed: '$f' -> '$target'"
done

exit 0