#!/usr/bin/env bash

set -euo pipefail

pushd web-actions/

. bin/activate-hermit

# Log all the commands and output, as this build is notoriously difficult to debug.
set -x

bin/npm ci
bin/npx webpack --mode production
bin/npm run-script format
bin/npm run-script test

popd

rm -rf build/web-actions
mkdir -p build/web-actions/web/_tab
mv web-actions/lib build/web-actions/web/_tab/web-actions
