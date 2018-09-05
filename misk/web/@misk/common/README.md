Misk Common
---
![](https://raw.githubusercontent.com/square/misk/master/misk.png)
This package provides shared libraries, externals, and styles across Misk tab repos.

Getting Started
---
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

- Use `@misk/common` externals to keep Webpack from bundling duplicate libraries and styles into your Misk module. Add the following to your `webpack.config.js`.
  
  ```Typescript
  const MiskCommon = require('@misk/common')

  ...

  module.exports = {
    mode
    entry
    ...
    externals: MiskCommon.externals,
  }

  ```

Included Libraries
---
From `package.json`:

```JSON
  "@blueprintjs/core": "^3.3.0",
  "@blueprintjs/icons": "^3.0.0",
  "axios": "^0.18.0",
  "connected-react-router": "^4.4.1",
  "dayjs": "^1.7.5",
  "history": "^4.7.2",
  "immutable": "^3.8.2",
  "react": "^16.4.2",
  "react-dom": "^16.4.2",
  "react-helmet": "^5.2.0",
  "react-hot-loader": "^4.3.4",
  "react-redux": "^5.0.7",
  "react-router": "^4.3.1",
  "react-router-dom": "^4.3.1",
  "react-router-redux": "^5.0.0-alpha.9",
  "react-transition-group": "^2.4.0",
  "redux": "^4.0.0",
  "redux-saga": "^0.16.0",
  "skeleton-css": "^2.0.4",
  "styled-components": "^3.4.2"
```

Included Styles
---
```Typescript
  import '../node_modules/@blueprintjs/core/lib/css/blueprint.css'
  import '../node_modules/normalize.css/normalize.css'
  import '../node_modules/skeleton-css/css/skeleton.css'
  import './styles/misk-common.css'
```

Contributing
---
#Adding a new package
- `yarn add {package}` to add the package to `package.json`
- Add package to window variable mapping to `src/externals.ts`
- Add window to javascript require include in `src/vendors.js`
- Update `README.md` with a copy of the updated `package.json` list of packages

Webpack Configs
---
- `webpack.config.js`: Exports common variables including `MiskCommon.Externals`
- `webpack.static.config.js`: Exports common styles file
- `webpack.vendor.config.js`: Exports common vendors library file

[Releasing](https://github.com/square/misk/blob/master/misk/web/%40misk/RELEASING.md)
---