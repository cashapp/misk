@misk/common
---

`@misk/common` provides shared libraries, externals, and styles between `@misk` modules.

Getting Started
---

- Install `@misk/common`???

```bash
$ yarn install --save @misk/common
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
  const miskCommon = require('@misk/common/lib');
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
  "@types/history": "^4.6.2",
  "@types/react": "^16.3.16",
  "@types/react-dom": "^16.0.5",
  "frint": "^5.4.4",
  "frint-config": "^5.4.4",
  "frint-react": "^5.4.4",
  "frint-router": "^5.4.4",
  "frint-router-react": "^5.4.4",
  "frint-store": "^5.4.4",
  "lodash": "^4.17.10",
  "prop-types": "^15.6.1",
  "react": "^16.4.0",
  "react-dom": "^16.4.0",
  "react-transition-group": "^2.3.1",
  "rxjs": "^6.2.0",
  "skeleton-css": "^2.0.4"
```

Included Styles
---
```Typescript
  import './styles/misk-common.css'
  import '../node_modules/@blueprintjs/core/lib/css/blueprint.css'
  import '../node_modules/normalize.css/normalize.css'
  import '../node_modules/skeleton-css/css/skeleton.css'
```