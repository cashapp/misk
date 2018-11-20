FROM node:alpine

ENV NODE_PATH='/node_modules'

COPY misk-web /bin/
RUN apk add --no-cache tini

# Populate yarn cache by installing all @misk/ packages
RUN yarn add \
  --pure-lockfile \
  --non-interactive \
  --modules-folder ${NODE_PATH} \
  @misk/common \
  @misk/components \
  @misk/dev \
  @misk/tslint \
  && yarn global add cross-env prettier webpack webpack-cli webpack-dev-server

WORKDIR ${NODE_PATH}
RUN /bin/misk-web -m 2>&1 > ${NODE_PATH}/.hash

WORKDIR /web
ENTRYPOINT ["/sbin/tini", "--"]
CMD tail -f /dev/null
