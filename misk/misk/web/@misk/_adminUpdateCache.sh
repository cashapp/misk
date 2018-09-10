#!/bin/sh

for dir in $(pwd)/*/lib/
do
    dir=${dir%*/}
    mkdir -p $(pwd)/_admin/@misk/
    cp -r $dir/* $(pwd)/_admin/@misk/
done