# Misk (Microservice Kontainer)

Misk is a new open source application container from Square.

Misk is not ready for use. The API is not stable.


# Admin Web App Resource Mapping

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



<script src="/js/core.js">


http://localhost:8080/_admin/js/core.js
http://localhost:8080/js/core.js

