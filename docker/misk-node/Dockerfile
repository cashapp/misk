FROM node:alpine

RUN apk add --no-cache tini

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
  && yarn global add webpack webpack-cli webpack-dev-server

ENTRYPOINT ["/sbin/tini", "-vvv", "--"]
CMD tail -f /dev/null