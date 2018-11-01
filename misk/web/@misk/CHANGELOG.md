## Breaking Changes in @misk packages

- 2018-11-01: `@misk/dev@^0.0.47` and `@misk/common@^0.0.52`. Prettier integration, Slug now injected into `index.html`.

  Replace `src/index.html` with the following:

  ```HTML
  <!DOCTYPE html>
  <html>

  <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0">
      <meta http-equiv="X-UA-Compatible" content="ie=edge">
  </head>

  <body>
      <div id="<%= htmlWebpackPlugin.options.slug %>"></div>

      <!-- Misk Libraries -->
      <script type="text/javascript" src="/@misk/common/styles.js" async></script>
      <script type="text/javascript" src="/@misk/common/vendors.js" preload></script>
      <script type="text/javascript" src="/@misk/common/common.js" preload></script>
      <script type="text/javascript" src="/@misk/components/components.js" preload></script>
  </body>

  </html>
  ```

  Create a file `prettier.config.js` with the following:

  ```Javascript
  const { createPrettierConfig } = require("@misk/dev")
  module.exports = createPrettierConfig()
  ```

  Replace all imports of `styled-components` with `react-emotion`.

  Add the following to `package.json` and add it as a prerequisite to `build` and `start` steps.

  ```JSON
  "lint": "prettier --write --config prettier.config.js \"./src/**/*.{md,css,sass,less,json,js,jsx,ts,tsx}\"",
  ```

  Change `miskTabBuilder` to `createTabWebpack` in `webpack.config.js`.

  Change `makeExternals` to `createExternals` in `webpack.config.js`.

- 2018-10-28: `@misk/common@^0.0.52`. `createApp()` and `createIndex()`

  Replace `src/index.tsx` with the following:

  ```Typescript
  import { createApp, createIndex } from "@misk/components"
  import * as Ducks from "./ducks"
  import routes from "./routes"
  export * from "./components"
  export * from "./containers"

  createIndex("config", createApp(routes), Ducks)
  ```

  Delete `src/App.tsx`.
