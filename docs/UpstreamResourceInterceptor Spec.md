# Admin Web App Resource Mapping Spec

Authors
- @swankjesse
- @adrw

Browser Endpoints
 * https://cloud-contacts.cashservices.com/_admin/config
 * https://cloud-contacts.cashservices.com/_admin/guice
 * https://cloud-contacts.cashservices.com/_admin/threads

All of these return the HTML from /_admin/index.html)

Referenced Content
 * https://cloud-contacts.cashservices.com/_admin/js/vendor.js
 * https://cloud-contacts.cashservices.com/_admin/js/core.js
 * https://cloud-contacts.cashservices.com/_admin/js/config.js
 * https://cloud-contacts.cashservices.com/_admin/js/guice.js
 * https://cloud-contacts.cashservices.com/_admin/js/core.js.map
 * https://cloud-contacts.cashservices.com/_admin/js/config.js.map
 * https://cloud-contacts.cashservices.com/_admin/css/core.css
 * https://cloud-contacts.cashservices.com/_admin/css/config.css

Misk JAR File (compiled stuff)

A bunch of code:

 * misk/Action.class
 * misk/MiskModule.class
 * web/_admin/js/vendor.js
 * web/_admin/js/core.js
 * web/_admin/js/config.js
 * web/_admin/js/guice.js

During Misk development we rely on the Webpack dev server to compile & serve assets. 

We serve these

 * https://localhost:8080/_admin/config         serve /_admin/index.html
 * https://localhost:8080/_admin/js/vendor.js   upstreamed to  http://localhost:3000/js/vendor.js
 * https://localhost:8080/_admin/js/core.js     upstreamed to  http://localhost:3000/js/core.js
 * https://localhost:8080/_admin/js/config.js   upstreamed to  http://localhost:3000/js/config.js

Development -> upstream to webpack server
Production  -> serve compiled assets from .jar file

During Service development, we get most of these from a .jar file

 * https://localhost:8080/_admin/config               serve /_admin/index.html
 * https://localhost:8080/_admin/js/vendor.js         served from jar
 * https://localhost:8080/_admin/js/core.js           served from jar
 * https://localhost:8080/_admin/js/urlshortener.js   upstreamed to http://localhost:3001/js/urlshortener.js



| PATH                  | DEVELOPMENT                               | PRODUCTION                        |
| --------------------- | ------------------------------------------| --------------------------------- |
| /_admin/js/core.js    | http://localhost:3000/_admin/js/core.js   | misk.jar web/_admin/js/core.js    |
| /_admin/config        | http://localhost:3000/_admin/             | misk.jar web/_admin/index.html    |
| /_admin/guice         | http://localhost:3000/_admin/             | misk.jar web/_admin/index.html    |
| /                     | http://localhost:3001/                    | urlshortener.jar web/index.html   |

`<script src="/js/core.js">`


http://localhost:8080/_admin/js/core.js
http://localhost:8080/js/core.js

Development Notes for Upstream Resource Interceptor
---

TODO: call the configured upstream (ie. webpack) server
if it 404s, call chain.proceed()
otherwise return the upstream's response

request has path, find mapping that satisfies
if mapping found, make http request to target server
for that request, do fancy stuff to create new url
ie. drop everything below localprefix path, append to new
Then. make okhttp request with that path, same headers, same method, body
execute request, take response, route response back

if no mapping found, call chain.proceed()

methods misk request -> okhttp requst / misk response -> okhjttp response will be shared
put them in misk request/response. misk request.toOkHttp3 and okhttp3 response.toMiskResponse

bodies aren't the same. build sink from source by writing source to the sink
ignore web socket completely

how to make adding mapping easy with new modules
Injection will do wiring
but... is rule going to be that when new service is built that redirection automatically happens on set paths

Extra challenge: deploying in production, bundle JS in JAR. In dev, JS served by webpack dev server
new service (ie. url shorteneer) consumes misk package as binary.

will have to inject/create okhttp3 client