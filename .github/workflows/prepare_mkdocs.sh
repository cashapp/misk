#!/bin/bash

# The website is built using MkDocs with the Material theme.
# https://squidfunk.github.io/mkdocs-material/
# It requires Python to run.
# Install the packages with the following command:
# pip install mkdocs mkdocs-material

set -ex

# Generate the API docs
# Restore the following command when this issue is fixed: https://github.com/cashapp/misk/issues/2056
#./gradlew --no-daemon dokkaGfm

preserve_index() {
	echo "" >> docs/0.x/tmpindex.md
	cat docs/0.x/index.md >> docs/0.x/tmpindex.md
}

./gradlew --no-daemon misk:dokkaGfm ; mv docs/0.x/index.md docs/0.x/tmpindex.md
./gradlew --no-daemon misk-actions:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-admin:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-aws:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-aws-dynamodb:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-aws-dynamodb-testing:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-aws2-dynamodb:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-aws2-dynamodb-testing:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-core:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-cron:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-crypto:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-datadog:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-eventrouter:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-events:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-events-core:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-events-testing:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-feature:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-feature-testing:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-gcp:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-gcp-testing:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-grpc-reflect:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-grpc-tests:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-hibernate:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-hibernate-testing:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-inject:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-jdbc:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-jdbc-testing:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-jobqueue:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-jobqueue-testing:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-jooq:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-launchdarkly:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-launchdarkly-core:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-metrics:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-metrics-digester:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-metrics-testing:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-policy:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-policy-testing:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-prometheus:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-redis:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-service:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-slack:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-testing:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-transactional-jobqueue:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-warmup:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-zookeeper:dokkaGfm ; preserve_index
./gradlew --no-daemon misk-zookeeper-testing:dokkaGfm ; preserve_index
./gradlew --no-daemon wisp-aws-environment:dokkaGfm ; preserve_index
./gradlew --no-daemon wisp-client:dokkaGfm ; preserve_index
./gradlew --no-daemon wisp-config:dokkaGfm ; preserve_index
./gradlew --no-daemon wisp-containers-testing:dokkaGfm ; preserve_index
./gradlew --no-daemon wisp-deployment:dokkaGfm ; preserve_index
./gradlew --no-daemon wisp-deployment-testing:dokkaGfm ; preserve_index
./gradlew --no-daemon wisp-feature:dokkaGfm ; preserve_index
./gradlew --no-daemon wisp-feature-testing:dokkaGfm ; preserve_index
./gradlew --no-daemon wisp-logging:dokkaGfm ; preserve_index
./gradlew --no-daemon wisp-logging-testing:dokkaGfm ; preserve_index
./gradlew --no-daemon wisp-resource-loader:dokkaGfm ; preserve_index
./gradlew --no-daemon wisp-resource-loader-testing:dokkaGfm ; preserve_index
./gradlew --no-daemon wisp-ssl:dokkaGfm ; preserve_index
mv docs/0.x/tmpindex.md docs/0.x/index.md

# Dokka filenames like `-http-url/index.md` don't work well with MkDocs <title> tags.
# Assign metadata to the file's first Markdown heading.
# https://www.mkdocs.org/user-guide/writing-your-docs/#meta-data
title_markdown_file() {
  TITLE_PATTERN="s/^[#]+ *(.*)/title: \1 - Misk/"
  echo "---"                                                     > "$1.fixed"
  cat $1 | sed -E "$TITLE_PATTERN" | grep "title: " | head -n 1 >> "$1.fixed"
  echo "---"                                                    >> "$1.fixed"
  echo                                                          >> "$1.fixed"
  cat $1                                                        >> "$1.fixed"
  mv "$1.fixed" "$1"
}

set +x
for MARKDOWN_FILE in $(find docs/0.x/ -name '*.md'); do
  echo $MARKDOWN_FILE
  title_markdown_file $MARKDOWN_FILE
done
set -x

# Copy in special files that GitHub wants in the project root.
cat README.md | grep -v 'project website' > docs/index.md
cp CHANGELOG.md docs/changelog.md
cp CONTRIBUTING.md docs/contributing.md
cp RELEASING.md docs/releasing.md
cp RELEASING-MANUAL.md docs/releasing-manual.md
