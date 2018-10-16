#!/bin/sh
# Build all @misk/ packages and tabs
# Run in web/ directory

for dir in "$(pwd)"/*/*; do
    dir=${dir%*/}
    [ -d "$dir" ] && echo "[BUILD] $dir" && sh -c "cd $dir; yarn ci-build"
done