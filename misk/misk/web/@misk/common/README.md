Misk Common
---
![](https://raw.githubusercontent.com/square/misk/master/misk.png)
This package provides shared libraries, externals, and styles across Misk tab repos.

Getting Started
---
```bash
$ yarn add @misk/common
```

Automatic Inclusion
---
- If your Webpack config builds off of a template in `@misk/dev`, `vendors.js` and `styles.js` will automatically be included in the build of that repo.
- If your Webpack config does not build off of a template in `@misk/dev`, use the Manual Inclusion steps below.

Manual Inclusion
---
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

Included Libraries
---
From `package.json`:

```
  @blueprintjs/core
  @blueprintjs/icons
  axios
  connected-react-router
  dayjs
  history
  immutable
  react
  react-dom
  react-helmet
  react-hot-loader
  react-redux
  react-router
  react-router-dom
  react-router-redux
  react-transition-group
  redux
  redux-saga
  skeleton-css
  styled-components
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
- Add package to `vendorExternals` in `@misk/dev/externals.js`
- Add window to javascript require include in `src/vendors.js`
- Update `README.md` with a copy of the updated `package.json` list of packages

Webpack Configs
---
- `webpack.config.js`: Exports common variables and functions
- `webpack.static.config.js`: Exports common styles file
- `webpack.vendor.config.js`: Exports common vendors library file

[Releasing](https://github.com/square/misk/blob/master/misk/web/%40misk/RELEASING.md)
---