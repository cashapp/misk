@misk/common
---

`@misk/common` provides shared libraries, externals, and styles between `@misk` modules.

Getting Started
---

- Install `@misk/common`

```bash
$ yarn add @misk/common
```

- Using the common vendors libraries and styles. We use [`copy-webpack-plugin`](https://github.com/webpack-contrib/copy-webpack-plugin) to copy the compiled `vendor.js` file into build folder. 
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

- Use `@misk/common` externals to keep Webpack from bundling duplicate libraries and styles into your Misk module. Add the following to your `webpack.config.js`
  
  ```Typescript
  const miskCommon = require('@misk/common');
  ...

  module.exports = {
    mode
    entry
    ...
    externals: miskCommon.externals,
  }

  ```

Included Libraries
---

```JSON
  "@blueprintjs/core": "^2.3.1",
  "@blueprintjs/icons": "^2.2.1",
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
  import './styles/misk-common.css'
  import '../node_modules/@blueprintjs/core/lib/css/blueprint.css'
  import '../node_modules/normalize.css/normalize.css'
  import '../node_modules/skeleton-css/css/skeleton.css'
```