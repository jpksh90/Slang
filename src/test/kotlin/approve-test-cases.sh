#!/bin/sh

for file in *.received.txt; do
    new_file=$(echo "$file" | sed 's/\.received\.txt$/.approved.txt/')
    mv "$file" "$new_file"
done

# shellcheck disable=SC2035
git add *.approved.txt