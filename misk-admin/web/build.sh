#!/usr/bin/env bash

set -ueo pipefail

# Ensure the proper hermit environment is activated
pushd ..
. bin/activate-hermit
popd

# Log all the commands and output, as this build is notoriously difficult to debug.
set -x

# This needs to run from the project root dir, so that the node_modules directory is
# just inside HERMIT_ENV. This is because the hermit node package explicitly adds
# "$HERMIT_ENV/node_modules/.bin" to the PATH, and we need that for miskweb to be found.
# Except sometimes we have a different hermit env active, so we deal with that below.
pushd ..
bin/npm ci --registry=https://registry.npmjs.org/
popd

../node_modules/.bin/miskweb ci-build -e

rm -rf build/web
mv web/tabs/database/lib build/web
