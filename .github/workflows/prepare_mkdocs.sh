#!/bin/bash

# The website is built using MkDocs with the Material theme.
# https://squidfunk.github.io/mkdocs-material/
# It requires Python to run.
# Install the packages with the following command:
# pip install mkdocs mkdocs-material

set -e

sed --version


# Dokka filenames like `-http-url/index.md` don't work well with MkDocs <title> tags.
# Assign metadata to the file's first Markdown heading.
# https://www.mkdocs.org/user-guide/writing-your-docs/#meta-data
process_dokka_file() {
  file_path="$1"
  TITLE_PATTERN="s/^[#]+ *(.*)/title: \"\1 | Misk 0.x API\"/"
  TITLE=$(cat "$file_path" | sed -E "$TITLE_PATTERN" | grep "title: " | head -n 1)
  CONTENT=$(cat "$file_path")

  # If README exists, get contents and put at top of the file and remove the separate README.md file
  dir_path=$(dirname "$file_path")
  readme_path="$dir_path/readme.md"
  if [ -f "$readme_path" ] && [ "$readme_path" != "$file_path" ]; then
    README_CONTENT=$(cat "$readme_path")
    CONTENT="$README_CONTENT\n\n# Misk 0.x API\n\n$CONTENT"
    rm "$readme_path"
  fi

  echo -e "---\n$TITLE\n---\n\n$CONTENT" > "$1"
}

echo "Prepare Misk Docs Site"

echo "Copy in module README docs to 0.x Dokka output"
src_dir="."
target_dir="docs/0.x"
# Use find to locate all README.md files in all subdirectories
find "$src_dir" -name 'README.md' -print0 | while IFS= read -r -d '' file; do
    # Basename of directory
    dir=$(basename "$(dirname "$file")")
    # Create the target directory, flatten directory structure to match Dokka output format
    mkdir -p "$target_dir/$dir"
    # Copy the file
    cp "$file" "$target_dir/$dir/readme.md"
done

echo "Fix misk/ and wisp/ relative links in module READMEs"
# TODO maybe run this on all files, not just 0.x
dir="docs/0.x"
# Use find to locate all files in the directory
find "$dir" -type f -name 'readme.md' -print0 | while IFS= read -r -d '' file; do
  # Use sed to replace the links for misk-* and wisp/wisp-* patterns
  sed -i '' -E 's|\]\((misk-.*)\)|](0.x/\1)|g' "$file"
  sed -i '' -E 's|\]\(wisp/(wisp-.*)\)|](0.x/wisp/\1)|g' "$file"
done

echo "Add titles to Dokka Markdown files"
find "docs/0.x" -name '*.md' -print0 | while IFS= read -r -d '' file; do
  if grep -q "title:" "$file" || [[ "$file" =~ readme\.md$ ]]; then
    continue
  fi

  echo $file
  process_dokka_file $file
done

echo "Copy in special files that GitHub wants in the project root"
cat README.md | grep -v 'project website' > docs/index.md
cp CHANGELOG.md docs/changelog.md
cp CONTRIBUTING.md docs/contributing.md
cp RELEASING.md docs/releasing.md

echo "Fix links to ./wisp/README.md"
if [ -f docs/0.x/wisp/readme.md ]; then
  mv docs/0.x/wisp/readme.md docs/0.x/wisp/index.md
fi

echo "Fix links to docs, images, misk-*/README.md, and wisp-*/README.md"
dir="docs"
find "$dir" -type f -name '*.md' -print0 | while IFS= read -r -d '' file; do
  if [ ! -f "$file" ]; then
    continue
  fi

  # Fix markdown links from special files to other docs
  sed -i '' -E 's|\(\./docs/(.*).md\)|(\1.md)|g' "$file"
  # Fix image references to ./docs/img/*
  sed -i '' -E 's|\(\./docs/(.*).png\)|(\1.png)|g' "$file"
  # Use sed to replace the links for wisp/README.md with wisp/index.md
  sed -i '' -E 's|\]\(./wisp/README.md\)|](0.x/wisp/index.md)|g' "$file"
  # Use sed to replace links to ./misk-*/README.md with ../0.x/misk-*/
  sed -i '' -E 's|\]\(./(misk-([^/]*))/README.md\)|](../0.x/misk-\2/)|g' "$file"
  # Use sed to replace the links for wisp/wisp-abc/README.md to wisp-abc/
  sed -i '' -E 's|\]\(wisp-([^/]*)/README.md\)|](../wisp-\1/index.md)|g' "$file"
  # Use sed to replace the links for ../wisp-abc/README.md to ../wisp-abc/
  sed -i '' -E 's|\]\(../wisp-([^/]*)/README.md\)|](../wisp-\1/index.md)|g' "$file"
done

echo "Fix links to wisp-*/README.md from misk-* modules"
dir="docs/0.x"
find "$dir" -type f -name 'index.md' -print0 | while IFS= read -r -d '' file; do
  if ! grep -q "misk-" "$file"; then
    continue
  fi
  # Use sed to replace the links for ../wisp/wisp-abc/README.md to ../wisp-abc/
  sed -i '' -E 's|\]\(../wisp/wisp-([^/]*)/README.md\)|](../wisp-\1/index.md)|g' "$file"
done

# TODO automatically parse 0.x docs and add to nav in mkdocs.yml
