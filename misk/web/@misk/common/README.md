@misk/common
---

`@misk/common` provides shared libraries, externals, and styles between `@misk` modules.

Getting Started
---

- Install `@misk/common`

```bash
$ yarn add @misk/common
```

- Using the common vendors libraries and styles. We use [`copy-webpack-plugin`](https://github.com/webpack-contrib/copy-webpack-plugin) to copy the compiled `vendor.js` and `styles.js` files into build folder.
  - Install [`copy-webpack-plugin`](https://github.com/webpack-contrib/copy-webpack-plugin)
    
    ```bash
    npm i -D copy-webpack-plugin
    ```

  - Add the following to your `webpack.config.js`

    ```Typescript
    const CopyWebpackPlugin = require('copy-webpack-plugin');
    ...

    module.exports = {
      mode
      entry
      ...
      plugins: [
        new CopyWebpackPlugin(
          [{ from: './node_modules/@misk/common/lib' }], 
          { debug: 'info', copyUnmodified: true }
        )
      ],
    }
    ```
  
  - Add the following to your `index.html` to import `styles.js` and `vendors.js`

    ```HTML
    <body>

      ...

      <!-- Misk Common Libraries -->
      <script type="text/javascript" src="js/styles.js" async></script>
      <script type="text/javascript" src="js/vendors.js" preload></script>

      <!-- Other JS -->
    </body>
    ```

- Use `@misk/common` externals to keep Webpack from bundling duplicate libraries and styles into your Misk module. Add the following to your `webpack.config.js`. We are working on a way to export this directly out of `@misk/common` so manual declaration of externals is not required.
  
  ```Typescript
  const miskCommonExternals = [{
    "@blueprintjs/core": {
      amd: "@blueprintjs/core",
      commonjs: "@blueprintjs/core",
      commonjs2: "@blueprintjs/core",
      root: "window.BlueprintjsCore",
    }
  }, {
    "@blueprintjs/icons": {
      amd: "@blueprintjs/icons",
      commonjs: "@blueprintjs/icons",
      commonjs2: "@blueprintjs/icons",
      root: "window.BlueprintjsIcons",
    }
  }, {
    "axios": {
      amd: "axios",
      commonjs: "axios",
      commonjs2: "axios",
      root: "window.Axios",
      },
  }, {
    "history": {
      amd: "history",
      commonjs: "history",
      commonjs2: "history",
      root: "window.History",
      },
  }, {
    "@misk/common": {
      amd: "@misk/common",
      commonjs: "@misk/common",
      commonjs2: "@misk/common",
      root: "window.MiskCommon",
      },
  }, {
    "@misk/components": {
      amd: "@misk/components",
      commonjs: "@misk/components",
      commonjs2: "@misk/components",
      root: "window.MiskComponents",
      },
  }, {
    "@misk/dev": {
      amd: "@misk/dev",
      commonjs: "@misk/dev",
      commonjs2: "@misk/dev",
      root: "window.MiskDev",
      },
  }, {
    "react": {
      amd: "react",
      commonjs: "react",
      commonjs2: "react",
      root: "window.React",
    },
  }, {
    "react-dom": {
      amd: "react-dom",
      commonjs: "react-dom",
      commonjs2: "react-dom",
      root: "window.ReactDOM",
    }
  }, {
    "react-helmet": {
      amd: "react-helmet",
      commonjs: "react-helmet",
      commonjs2: "react-helmet",
      root: "window.ReactHelmet",
    },
  }, {
    "react-hot-loader": {
      amd: "react-hot-loader",
      commonjs: "react-hot-loader",
      commonjs2: "react-hot-loader",
      root: "window.ReactHotLoader",
      },
  }, {
    "react-redux": {
      amd: "react-redux",
      commonjs: "react-redux",
      commonjs2: "react-redux",
      root: "window.ReactRedux",
      },
  }, {
    "react-router": {
      amd: "react-router",
      commonjs: "react-router",
      commonjs2: "react-router",
      root: "ReactRouter",
      },
  }, {
    "react-router-dom": {
      amd: "react-router-dom",
      commonjs: "react-router-dom",
      commonjs2: "react-router-dom",
      root: "window.ReactRouterDom",
      },
  }, {
    "redux": {
      amd: "redux",
      commonjs: "redux",
      commonjs2: "redux",
      root: "window.Redux",
      },
  }, {
    "styled-components": {
      amd: "styled-components",
      commonjs: "styled-components",
      commonjs2: "styled-components",
      root: "window.StyledComponents",
      },
  }]

  ...

  module.exports = {
    mode
    entry
    ...
    externals: miskCommonExternals,
  }

  ```

Included Libraries
---

From `package.json`:

```JSON
  "@blueprintjs/core": "^3.0.1",
  "@blueprintjs/icons": "^3.0.0",
  "axios": "^0.18.0",
  "connected-react-router": "^4.3.0",
  "history": "^4.7.2",
  "react": "^16.4.1",
  "react-dom": "^16.4.1",
  "react-helmet": "^5.2.0",
  "react-hot-loader": "^4.3.3",
  "react-redux": "^5.0.7",
  "react-router": "^4.3.1",
  "react-router-dom": "^4.3.1",
  "react-transition-group": "^2.2.1",
  "redux": "^4.0.0",
  "skeleton-css": "^2.0.4",
  "styled-components": "^3.3.3"
```

Included Styles
---
```Typescript
  import '../node_modules/@blueprintjs/core/lib/css/blueprint.css'
  import '../node_modules/normalize.css/normalize.css'
  import '../node_modules/skeleton-css/css/skeleton.css'
  import './styles/misk-common.css'
```