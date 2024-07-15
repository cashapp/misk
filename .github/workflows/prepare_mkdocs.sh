#!/bin/bash

# The website is built using MkDocs with the Material theme.
# https://squidfunk.github.io/mkdocs-material/
# It requires Python to run.
# Install the packages with the following command:
# pip install mkdocs mkdocs-material

set -e

# Dokka filenames like `-http-url/index.md` don't work well with MkDocs <title> tags.
# Assign metadata to the file's first Markdown heading.
# https://www.mkdocs.org/user-guide/writing-your-docs/#meta-data
title_markdown_file() {
  TITLE_PATTERN="s/^[#]+ *(.*)/title: \1 - Misk/"
  TITLE=$(cat $1 | sed -E "$TITLE_PATTERN" | grep "title: " | head -n 1)
  CONTENT=$(cat $1)
  echo -e "---\n$TITLE\n---\n\n$CONTENT" > "$1"
}

echo "Prepare Misk Docs Site"

# TODO automatically parse 0.x docs and add to nav in mkdocs.yml

echo "Add titles to Dokka Markdown files"
for MARKDOWN_FILE in $(find docs/0.x/ -name '*.md'); do
  if grep -q "title:" $MARKDOWN_FILE; then
    continue
  fi

  echo $MARKDOWN_FILE
  title_markdown_file $MARKDOWN_FILE
done

echo "Copy in special files that GitHub wants in the project root"
cat README.md | grep -v 'project website' > docs/index.md
cp CHANGELOG.md docs/changelog.md
cp CONTRIBUTING.md docs/contributing.md
cp RELEASING.md docs/releasing.md

echo "Copy in Wisp docs"
src_dir="wisp"
target_dir="docs"
# Use find to locate all README.md files in all subdirectories
find "$src_dir" -name 'README.md' -print0 | while IFS= read -r -d '' file; do
    # Create the target directory, preserving the directory structure
    mkdir -p "$target_dir/$(dirname "$file")"
    # Copy the file
    cp "$file" "$target_dir/$(dirname "$file")/index.md"
done

echo "Fix docs/ relative links in special files"
dir="docs"
find "$dir" -name '*.md' -print0 | while IFS= read -r -d '' file; do
  # Fix markdown links
  # Use sed to replace the links and store the result in a variable
  modified_content=$(sed -E 's|\(\./docs/(.*).md\)|(\1.md)|g' "$file")
  # Echo the modified content back into the file
  echo "$modified_content" > "$file"

  # Fix image references
  # Use sed to replace the links and store the result in a variable
  modified_content=$(sed -E 's|\(\./docs/(.*).png\)|(\1.png)|g' "$file")
  # Echo the modified content back into the file
  echo "$modified_content" > "$file"
done

echo "Fix wisp/ relative links in special files"
dir="docs"
find "$dir" -name '*.md' -print0 | while IFS= read -r -d '' file; do
  # Use sed to replace the links and store the result in a variable
  modified_content=$(sed -E 's|\(\.*/wisp/(.*).md\)|(\wisp/index.md)|g' "$file")
  # Echo the modified content back into the file
  echo "$modified_content" > "$file"
done

echo "Fix wisp/ relative links"
dir="docs/wisp"
# Use find to locate all files in the directory
find "$dir" -type f -print0 | while IFS= read -r -d '' file; do
  # Use sed to replace the links and store the result in a variable
  modified_content=$(sed -E 's|\(\wisp-(.*)/(.*).md\)|(wisp-\1/index.md)|g' "$file")
  # Echo the modified content back into the file
  echo "$modified_content" > "$file"
done
