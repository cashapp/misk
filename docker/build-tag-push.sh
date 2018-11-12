#!/bin/sh

# usage: ./build-tag-push.sh <image name> <tag>

org="squareup/"
name=${1%/}
version=$2

echo "[BUILD] ${org}${name}:${version}"
docker build --no-cache -t "${org}${name}:${version}" ./${name}

echo "[TAG] ${org}${name}:latest"
docker tag "${org}${name}:${version}" "${org}${name}:latest"

echo "[PUSH] ${org}${name}:${version}"
docker push "${org}${name}:${version}"

echo "[PUSH] ${org}${name}:latest"
docker push "${org}${name}:latest"

echo "[INSPECT] ${org}${name}:${version} shipped with following @misk/ NPM packages"
docker run -it --rm ${org}${name}:${version} yarn cache list --pattern @misk
