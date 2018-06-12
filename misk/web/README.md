Misk Admin
---

## Organization
- `main`: core UI for `_admin/` dashboard
- `@misk`: all npm packages
  - `@misk/common`: common libraries and files that `modules` and `main` have access to
- `modules`: all dashboard tabs written as "child apps" that can be hot loaded into main at runtime
- `original-web`: heartbeat code, will be moved into modules and deleted

## Framework and Languages
- Typescript + ReactJS
- Modularity enabled with [Frint.js](https://frint.js.org/)
  - Allows for child apps written in Vanilla.js, ReactJS, or VueJS to work together
  - All child apps (`modules`) register to a "Region" React component declared in Root App (`main`)
  - Common store in Root App for state. Common store is accessible and extensible in child apps
  - Common router in Root App. Common router is accessible and extensible in child apps
- [Blueprintjs](http://blueprintjs.com/) for UI elements
- [FontAwesome](https://fontawesome.com/) for Icons
- [Skeleton](http://getskeleton.com/) for very simple responsive boilerplate styling

## Getting Started

```
$ cd main
$ yarn run reinstall
$ yarn start
```

## Progress

- [x] FrintJS Multiple-App Exemplar refactored for Misk testing
- [x] Separate vendor.js runtime inclusion for common libraries
- [x] No compile time dependencies on modules
- [x] Page loads, then in browser console the following ES6 Imports will dynamically load the test modules
  
  ```
  $ import(`./js/log4j.js`)
  $ import(`./js/threads.js`)
  ```

- [x] Move code to `src`
- [x] Move all code into main Misk repo, create PR
- [ ] Publish `@misk/common` NPM package so it can be accessed by all modules (and they don't need to `require` the path name)
- [ ] Wire up admin within Kotlin, specifically for the URL shortener app
  - [ ] Flesh out URL shortener so the admin tabs load live data (ie. config, threads...)
  - [ ] Create easy front end for URL shortener (form that enters long url, shows short url below)
  - [ ] Create admin tab for urlshortner that shows all URLs in a table and allows paging of the database
  - [ ] @todo: React doesn't resolve js scripts not in `core.js` nicely. Current work around is copying the compiled module code into `src/core/components` at compile time so that modules correctly load when links are gone to. This work around is not needed if the long term initial loading mechanism is not through React Router but through an endpoint that calls `import(./path/to/module.js)`. Calling this in browser JS console works without the hacky fix described above (ie. `import(./t_config.js)`).
- [x] Move repos to Typescript
- [x] Move Redux flows to Typescript [Resource](https://rjzaworski.com/2016/08/getting-started-with-redux-and-typescript)
- [ ] Frint-Router (try AllowJS ts flag), otherwise add typings for the package
- [x] Create sidebar UI
- [ ] Have module registration also add button to sidebar
- [x] Have sidebar buttons trigger the Import of modules
- [ ] Dynamically extensible router. Each module gets a namespace and handles all routes within it
- [ ] Exemplar module UI

## Typescript Nuances
- Some NPM packages are written in Typescript, these are very easy to import and use without issue.
- Some NPM packages are **not** written in Typescript but include Typescript typings `filename.d.ts` files to allow use in Typescript projects.
- Some NPM packages do **not** have Typescript included typings but have community contributed ones in a NPM `@types/package-name` library. Check if it exists in the [DefinitelyTyped](https://github.com/DefinitelyTyped/DefinitelyTyped) repo.
- Some NPM packages have none of the above, are in pure JS, and you're out of luck. If there are alternative packages that fit into the three above categories, that is highly preferable so full Typescript safety checking can be ensured. If there are none, then the following methods will allow JS libraries to work:
  - You can be a good OSS citizen and add typings files to the packages you want to use.
  - Override Typescript using require imports `const module = require('package-name');` instead of es6 standard imports `import { module } from 'package-name';`.

  ## Assumptions
  - All modules and code are being run on network internal environments by authenticated employees
    - Thus: production webpack does output `source-map` for easier debugging of any production errors. If this framework were to be used outside of a trusted environment, then that should be removed since it does expose source code.


Kotlin Desired Flow
---
- main is already running
- all modules as they are spun up / injected join main

- need to save modules in state so they can render without loading the file each time. in module.tsx have if (!in state) import and save to state; else load from state.
- Maybe have a `misk.lock` file that modules can add themselves to a list that has their name/url so that they can be added to the top menu which seems to work for the live loading


Resources
---
- [Primary Strategy](https://stackoverflow.com/questions/44778265/dynamically-loading-react-components)
- [Dynamic Loading React Components](https://www.slightedgecoder.com/2017/12/03/loading-react-components-dynamically-demand/)
- [Webpack: export to existing module in window](https://stackoverflow.com/questions/30539725/webpack-export-to-existing-module-in-window?utm_medium=organic&utm_source=google_rich_qa&utm_campaign=google_rich_qa)

## Authors
- Andrew Paradi: @andrewparadi