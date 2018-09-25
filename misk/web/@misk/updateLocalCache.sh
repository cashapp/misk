#!/bin/sh

for dir in "$(pwd)"/*/lib/
do
    dir=${dir%*/}
    mkdir -p "$(pwd)"/@misk/
    cp -r $dir/* "$(pwd)"/@misk/
done

# Don't break build scripts
exit 0