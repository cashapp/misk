#!/bin/bash

# The website is built using MkDocs with the Material theme.
# https://squidfunk.github.io/mkdocs-material/
# It requires Python to run.
# Install the packages with the following command:
# pip install mkdocs mkdocs-material

set -ex

# Dokka filenames like `-http-url/index.md` don't work well with MkDocs <title> tags.
# Assign metadata to the file's first Markdown heading.
# https://www.mkdocs.org/user-guide/writing-your-docs/#meta-data
title_markdown_file() {
  TITLE_PATTERN="s/^[#]+ *(.*)/title: \1 - Misk/"
  TITLE=$(cat $1 | sed -E "$TITLE_PATTERN" | grep "title: " | head -n 1)
  CONTENT=$(cat $1)
  echo -e "---\n$TITLE\n---\n\n$CONTENT" > "$1"
}

set +x
for MARKDOWN_FILE in $(find docs/0.x/ -name '*.md'); do
  if grep -q "title:" $MARKDOWN_FILE; then
    continue
  fi

  echo $MARKDOWN_FILE
  title_markdown_file $MARKDOWN_FILE
done
set -x

# Copy in special files that GitHub wants in the project root.
cat README.md | grep -v 'project website' > docs/index.md
cp CHANGELOG.md docs/changelog.md
cp CONTRIBUTING.md docs/contributing.md

# Copy in Wisp docs
#mkdir -p docs/wisp
#cp wisp/README.md docs/wisp/readme.md

# Define the source and target directories
src_dir="wisp"
target_dir="docs"

# Use find to locate all README.md files in all subdirectories
set +x
find "$src_dir" -name 'README.md' -print0 | while IFS= read -r -d '' file; do
    # Create the target directory, preserving the directory structure
    mkdir -p "$target_dir/$(dirname "$file")"
    # Copy the file
    cp "$file" "$target_dir/$(dirname "$file")/index.md"
done
set -x

# Fix docs/ relative links in special files
dir="docs"
set +x
find "$dir" -name '*.md' -print0 | while IFS= read -r -d '' file; do
  # Use sed to replace the links and store the result in a variable
  modified_content=$(sed -E 's|\(\./docs/(.*).md\)|(\1/)|g' "$file")
  # Echo the modified content back into the file
  echo "$modified_content" > "$file"
done
set -x

# Fix wisp/ relative links
dir="docs/wisp"
set +x
# Use find to locate all files in the directory
find "$dir" -type f -print0 | while IFS= read -r -d '' file; do
  # Use sed to replace the links and store the result in a variable
  modified_content=$(sed -E 's|\(\wisp-(.*)/(.*).md\)|(/wisp/wisp-\1/)|g' "$file")
  # Echo the modified content back into the file
  echo "$modified_content" > "$file"
done
set -x

