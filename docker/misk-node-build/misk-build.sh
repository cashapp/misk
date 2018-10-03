#!/bin/sh

# Clean build all @misk/ packages and tabs
# Assumes following directory structure
# /web
#   misk-build.sh
#   tabs/
#     tab1/
#       package.json
#     tab2/
#       package.json
#     tab3/
#       package.json
#     ...

for dir in $(pwd)/*/*; do
    dir=${dir%*/}
    [ -d $dir ] && echo "[BUILD] $dir" && cd $dir; yarn gradle
done