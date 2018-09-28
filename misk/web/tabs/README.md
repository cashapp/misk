How to make a New Misk Tab
===

- For this guide, the following tab properties are used for example
  - name: T-Rex Food Log
  - slugified name: trexfoodlog
  - port: 30420
  - action name: TRexFoodLogAction


Directory Structure
---
- Any new tabs or `@misk/` packages live in a top level `web/` directory of the same level as your service's `src`, `build`, or `out` directories. 
- All tabs live in `web/tabs/` and `@misk/` packages live in `web/@misk/`. For most services, you will only need a `web/tabs/` directory.
- This structure is assumed by the Docker build containers and scripts. An example is included below.

```
  trex-service/
    build/
    out/
    src/
    web/
      tabs/
        trexangermanagement/
        trexhealthcheck/
        trexfoodlog/
          lib/
          node_modules/
          src/
            index.ts

          package.json
```

Wiring up a New Tab
---
- Copy the `tabs/example` tab in the [misk/misk/web repo](https://github.com/square/misk/tree/master/misk/web/tabs/example) to your service's `web/tabs` directory.
- Open `package.json` and update the following fields using your new tab name, slug, port...etc.
  - name: `misktab-trexfoodlog`. Package name must only have lowercase letters.
  - miskTabWebpack:
    - name: `T-Rex Food Log`. Titlecase tab name.
    - output_path: optional override field. By default it will be `lib`.
    - port: `30420`.  port number for Webpack Dev Server. 
      - Todo(adrw): Find a way to centrally reserve a port number. Otherwise there will be the risk that while working in development mode on your tab and another tab, the other tab may fail to serve because of port conflict.
      - `3100-3199`: Misk infrastructure (ex. Loader tab).
      - `3200-3499`: Shipped with Misk tabs (ex. Config).
      - `3500-9000`: Square reserved ports.
      - `30000-39999`: Open ports for all other tabs built on Misk.
    - relative_path_prefix: optional override field. By default, it will be tab slug prefixed by `_tab/`.
    - slug: lowercase, no symbols name to be used in determining URL domain space. Should be the same as the `package.json` -> name without the prefix `misktab-`.
  
  - Example
    ```JSON
    "name": "misktab-trexfoodlog",
    ...
    "miskTabWebpack": {
      "name": "T-Rex Food Log",
      "port": "30420",
      "slug": "trexfoodlog"
    }
    ```

- Open `src/index.html` and update the line `<div id="example">` to `<div id="trexfoodlog">`.
- Open `src/index.tsx ` and update the line `const tabSlug = "example"` to `const tabSlug = "trexfoodlog"`.

Configuring the Misk Service
---
- Add the following multibindings to the appropriate KAbstractModule.
  - Create a new module for the tab named `misk/src/main/kotlin/misk/web/metadata/{TabName}MetadataModule` and add the below multibindings to it. Install the tab module in the respective location:
    - If the tab is part of base `Misk`, then install that module in the list of tabs in `misk/src/main/kotlin/misk/web/AdminTabModule.kt`.
    - Else If it's a service specific tab, then add to your main service module (ie. for a `UrlShortenerFrontend` tab, install it in `UrlShortenerServiceModule`).
    - Else, install it in your main service module.

  ```Kotlin
  multibind<WebActionEntry>().toInstance(WebActionEntry<TRexFoodLogAction>())
  multibind<AdminTab>().toInstance(AdminTab(
      name = "T-Rex Food Log",
      slug = "trexfoodlog",
      url_path_prefix = "/_admin/trexfoodlog/",
      category = "T-Rex"
  ))
  multibind<StaticResourceEntry>()
      .toInstance(StaticResourceEntry("/_tab/trexfoodlog/", "classpath:/web/_tab/trexfoodlog"))
  ...
  if (environment == Environment.DEVELOPMENT) {
    ...
    multibind<WebActionEntry>().toInstance(
      WebActionEntry<WebProxyAction>("/_tab/trexfoodlog/"))
    multibind<WebProxyEntry>().toInstance(
      WebProxyEntry("/_tab/trexfoodlog/", "http://localhost:30420/"))
    ...
  } else {
    ...
    multibind<WebActionEntry>().toInstance(
      WebActionEntry<StaticResourceAction>("/_tab/trexfoodlog/"))
    ...
  }
  ```

  - The following explains why each multibinding is used:
    - WebActionEntry: Installs and configures a WebAction with optional prefix.
    - AdminTab: Metadata of the tab that is used to generate dashbaord menus and other views.
    - WebProxyAction: an endpoint that is used in tab development to forward tab requests to a Webpack-Dev-Server which is continuously building the tab as you edit.
    - WebProxyEntry: Binds a WebProxyAction to a specific `url_prefix` to match on and a URL to forward matching requests to. This is usually a local port for the tab's Webpack-Dev-Server
    - StaticResourceAction: returns the most recently compiled web code from either the `classpath` or from the jar.
    - StaticResourceEntry: Binds a StaticResourceAction to a url_prefix and a resource location to find the requested web assets in the `classpath` or jar. This should be bound regardless of environment since `WebProxyAction` will fall back to look at static resources if a Webpack-Dev-Server is not running.
  - Environment Differences
    - Live Editing a Tab: Use Webpack-Dev-Server in the specific tab you're editing to see edits live in the browser. The service should be run with Development environment and WebProxyAction bound so that all requests attempt to find a Webpack-Dev-Server. If requests fail, they return any matching static resources from `classpath` or jar. This is why a StaticResourceEntry must be bound regardless of environment.
    - In Development Mode but not Editing: WebProxyAction will still be bound but any requests that do not find a Webpack-Dev-Server will fall back to the previously compiled static resources in `classpath` or jar.
    - In Production: All web assets are served by StaticResourceAction from jar.

Adding your Tab Webpack Build to Gradle
---
Tab builds are kicked off by Gradle but done within a Docker container for portability across environments. The following steps will update your Gradle to be able to start the `misk-node` Docker container and to build your tab in it.

Add the following code to your service's `dependencies.gradle`.

```JSON
  "dockerGradlePlugin": "gradle.plugin.com.palantir.gradle.docker:gradle-docker:0.20.1",
```

If this is an internal Square service, leave the value of the above blank and pull the latest `polyrepo` pinned version with `./polyrepo deb-upgrade {service name}`.

In your service's central `build.gradle` add the following where appropriate. Primarily, you may need to add the maven repository pointing to `https://plugins.gradle.org/m2/` and `classpath dep.dockerGradlePlugin` to your dependencies. If your service has no subprojects, that section can be ignored.

```Gradle
  ...
  apply from: new File("./dependencies.gradle")
  ...
  subprojects {
    buildscript {
      repositories {
        ...
        maven {
          url "https://plugins.gradle.org/m2/"
        }
      }

      dependencies {
        ...
        classpath dep.dockerGradlePlugin
        ...
      }
    }
    repositories {
      ...
      maven {
        url "https://plugins.gradle.org/m2/"
      }
    }
  }

```

In your service's project `build.gradle` file you will need to add the following to configure the Docker plugin, start the container, and let Gradle spin off a build if there is a change in your tab code. Adjust the template below to fit your service's file structure and to use the most up to date [Docker image version](https://hub.docker.com/r/squareup/misk-node/tags/).

```Gradle
  apply plugin: "com.palantir.docker-run"
  
  ...
  def dockerContainerName = "{service}-{project}-gradle-${new Date().format("YMD-HMS")}"

  configurations {
    dockerRun {
      name "${dockerContainerName}"
      image 'squareup/misk-node:0.0.1'
      volumes 'web': '/web'
      daemonize true
      clean true
    }
  }

  ...

  sourceSets {
    ...
    main.resources {
      srcDirs += [
        'web/tabs/trexangermanagement/lib',
        'web/tabs/trexhealthcheck/lib',
        'web/tabs/trexfoodlog/lib'
      ]
      exclude '**/node_modules'
    }
  }

  ...

  task webpackTabsTRexFoodLog(type: Exec) {
    inputs.file("web/tabs/trexfoodlog/yarn.lock").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("web/tabs/trexfoodlog/src").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file("web/tabs/trexfoodlog/webpack.config.js").
            withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir("web/tabs/trexfoodlog/dist")
    outputs.cacheIf { true }

    executable "sh"
    args "-c", "docker exec ${dockerContainerName} sh -c 'cd /web/tabs/trexfoodlog; yarn gradle'"
  }

  jar.dependsOn webpackTabsTRexFoodLog
  jar.doLast { 'dockerStop' }

```

You'll notice the build runs the command `yarn gradle`. By default in the Example tab you used as a starter, `yarn gradle` expands to `yarn install && yarn build` so that even on CI (continuous integration) systems where `node_modules` have not been installed yet, the build still succeeds.

To confirm that your tab is shipping in the jar, you can run the following commands to build the jar, find it in your filesystem, browse the included files, and confirm that related compiled JS code is in your jar.

```Bash
  $ ./gradlew clean assemble jar
  $ find misk/build | grep jar
  $ jar -tf misk/build/libs/{your jar location found above}.jar | grep _tab/trexfoodlog/
```

Loading Data into your Misk Tab
---
- All data retrieval and processing is done within a Ducks module in `src/ducks`.
- `src/ducks/example.ts` contains detailed documentation on the purpose of a Ducks module and is used throughout the Example Tab to show example functionality.
- Best practice is to create a new Ducks module and copy necessary elements and wiring up techniques in from the `example` Ducks module. You can delete the `example.ts` file when it is no longer necessary.

Building your Tab
---
1. Kick off an initial build with Gradle `./gradlew clean jar`.
1. Start your primary Misk service in IntelliJ, or use `UrlShortenerService` for testing.
1. Open up [`http://localhost:8080/_admin/`](http://localhost:8080/_admin/) in the browser.

Developing your Tab
---
1. Follow the steps above to build all local tabs and start your service.
1. Run the following commands to spin up a Webpack-Dev-Server in Docker instance to serve live edits to your service.

```Bash


```

Other Development Notes
---
- Notice in the Misk multibindings that the AdminTabAction url had the prefix `_admin/` but all other multibindings had the prefix `_tab/`. This allows you to develop your tab without any of the surrounding Admin dashboard UI or overhead. Use the respective link below to open your tab in the browser.
  - [`http://localhost:8080/_admin/trexfoodlog/`](http://localhost:8080/_admin/trexfoodlog/): full dashboard UI with menu, other tabs...etc. Before the tab is pushed in production, extensive testing should be done here to ensure there are no bugs when the tab is loaded into the dashboard.
  - [`http://localhost:8080/_tab/trexfoodlog/`](http://localhost:8080/_tab/trexfoodlog/): develop your tab in the full browser window without dashboard nav bar or other UI. All functionality and styling should end up being identical to when the tab is loaded in the dashboard.