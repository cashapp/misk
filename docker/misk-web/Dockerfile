FROM node:alpine

RUN apk add --no-cache tini

WORKDIR /tmp/web/

# Populate yarn cache by installing all @misk/ packages
RUN yarn add \
  --pure-lockfile \
  --non-interactive \
  --modules-folder /node_modules \
  @misk/common \
  @misk/components \
  @misk/dev \
  @misk/tslint \
  && yarn global add cross-env prettier webpack webpack-cli webpack-dev-server

ENV NODE_PATH='/node_modules'
COPY misk-web /bin/
WORKDIR /web
ENTRYPOINT ["/sbin/tini", "--"]
CMD tail -f /dev/null