#!/bin/sh

# Runs webpack-dev-server on first found tab in the expected directory structure
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
    [ -d $dir ] && echo "[DEVELOP] $dir" && cd $dir; yarn start
    break
done