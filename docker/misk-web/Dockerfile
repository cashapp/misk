FROM node:alpine

RUN apk add --no-cache tini python python2 alpine-sdk

WORKDIR /tmp/web/

# Populate yarn cache by installing all @misk/ packages
RUN yarn add \
    --pure-lockfile \
    --non-interactive \
      @misk/common \
      @misk/components \
      @misk/dev \
      @misk/tslint \
  && rm -rf /tmp/web/node_modules \
  && yarn global add cross-env webpack webpack-cli webpack-dev-server

COPY misk-web /bin/
WORKDIR /web
ENTRYPOINT ["/sbin/tini", "--"]
CMD tail -f /dev/null