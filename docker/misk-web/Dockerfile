FROM node:alpine

RUN apk add --no-cache tini
ENV NODE_MODULES='/node_modules'

RUN yarn add \
  --pure-lockfile \
  --non-interactive \
  --modules-folder ${NODE_MODULES} \
  @misk/common \
  @misk/components \
  @misk/dev \
  @misk/tslint \
  && yarn global add cross-env prettier webpack webpack-cli webpack-dev-server

WORKDIR ${NODE_MODULES}
COPY misk-web /bin/
RUN /bin/misk-web -m 2>&1 > ${NODE_MODULES}/.hash

WORKDIR /web
ENTRYPOINT ["/sbin/tini", "--"]
CMD tail -f /dev/null
