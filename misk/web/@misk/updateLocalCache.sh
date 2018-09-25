#!/bin/sh

for dir in "$(pwd)/"*/lib/web/
do
    dir=${dir%*/}
    mkdir -p "$(pwd)/web"
    cp -r "$dir/"* "$(pwd)/web/"
done

# Don't break build scripts
exit 0