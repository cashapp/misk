# /web/static/cache

The few web dependencies for the admin dashboard and other frontend apps don't change frequently. 

To support easier local development, dependencies are checked in to the repo so the admin dashboard is available without `npm install` or any non-Gradle steps.

Some of the dependencies rely on each other and require minor manual alterations to the downloaded code to reference the local cached files instead of JS modules.
