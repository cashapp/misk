How to make a New Misk Tab
---

- For this document, the following tab properties are used for example
  - name: T-Rex Food Log
  - slugified name: trexfoodlog
  - port: 30420
  - action name: TRexFoodLogAction
- Duplicate the `tabs/example` tab
- Open `package.json` and update the following fields using your new tab name, slug, port...etc.
  - name: `misktab-trexfoodlog`. Package name must only have lowercase letters.
  - miskTabWebpack:
    - name: `T-Rex Food Log`. Titlecase tab name.
    - output_path: optional override field. By default it will be `dist`.
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
  multibind<WebActionEntry>().toInstance(WebActionEntry<NewTabAction>())
  multibind<AdminTab>().toInstance(AdminTab(
      name = "T-Rex Food Log",
      slug = "trexfoodlog",
      url_path_prefix = "/_admin/trexfoodlog/"
  ))
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
    multibind<StaticResourceEntry>()
      .toInstance(StaticResourceEntry("/_tab/trexfoodlog/", "classpath:/web/_tab/trexfoodlog"))
    ...
  }
  ```

Adding your Tab Webpack Build to Gradle
---

In your service's main `build.gradle` file you will need to add a task that will build your tab with Webpack, cache the resulting compiled code, and rebuild if the source code changes. Adjust the template below to fit your service's file structure.

```Gradle
  task webpackTabsTRexFoodLog(type: Exec) {
    inputs.file("web/tabs/trexfoodlog/package-lock.json").
            withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file("web/tabs/trexfoodlog/yarn.lock").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("web/tabs/trexfoodlog/src").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file("web/tabs/trexfoodlog/webpack.config.js").
            withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir("web/tabs/trexfoodlog/dist")
    outputs.cacheIf { true }

    workingDir "web/tabs/trexfoodlog"
    commandLine "yarn", "gradle"
  }

  jar.dependsOn webpackTabsTRexFoodLog

```

You'll notice the build runs the command `yarn gradle`. By default in the Example tab you used as a starter, `yarn gradle` expands to `npm install && yarn build` so that even on CI (continuous integration) systems where `node_modules` have not been installed yet, the build still succeeds.

You will also have to add a `from` to the service's jar task so that your compiled tab code is included in the service jar. Adjust the template below to fit your service's file structure.

```Gradle
  jar {
    into("web/") {
      from("./web/tabs/trexfoodlog/dist/")
    }
  }
```

Loading Data into your Misk Tab
---
- All data retrieval and processing is done within a Ducks module in `src/ducks`.
- `src/ducks/example.ts` contains detailed documentation on the purpose of a Ducks module and is used throughout the Example Tab to show example functionality.
- Best practice is to create a new Ducks module and copy necessary elements and wiring up techniques in from the `example` Ducks module. You can delete the `example.ts` file when it is no longer necessary.

Building your Tab
---
1. Run `./develop@misk.sh` in `web/` directory. This builds all `@misk/` packages and starts `@misk/` packages dev server)
1. Run `./build.sh` in `web/` directory. This installs and builds all `@misk/` packages and Tabs
1. For any tab that you'll be doing active development on, open a new Tmux/Terminal session and run

  ```
  $ cd tabs/trexfoodlog
  $ yarn start
  ```

1. Start your primary Misk service in IntelliJ, or use `UrlShortenerService` for testing
1. Open up [`http://localhost:8080/_admin/`](http://localhost:8080/_admin/) in the browser

Other Development Notes
---
- Notice in the Misk multibindings that the AdminTabAction url had the prefix `_admin/` but all other multibindings had the prefix `_tab/`. This allows you to develop your tab without any of the surrounding Admin dashboard UI or overhead. Use the respective link below to open your tab in the browser.
  - [`http://localhost:8080/_admin/trexfoodlog/`](http://localhost:8080/_admin/trexfoodlog/): full dashboard UI with menu, other tabs...etc. Before the tab is pushed in production, extensive testing should be done here to ensure there are no bugs when the tab is loaded into the dashboard.
  - [`http://localhost:8080/_tab/trexfoodlog/`](http://localhost:8080/_tab/trexfoodlog/): develop your tab in the full browser window. All functionality and styling should end up being identical to when the tab is loaded in the dashboard.