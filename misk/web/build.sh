#!/bin/sh
# Build all @misk/ packages and tabs

for dir in $(pwd)/*/*
do
    dir=${dir%*/}
    [ -d $dir ] && echo $dir
    [ -d $dir ] && bash -c "cd $dir; yarn install; yarn build"
done