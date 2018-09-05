Misk Dev
---
![](https://raw.githubusercontent.com/square/misk/master/misk.png)
This package provides shared devDependencies and compiler options used to provide a common development environment across Misk tab repos. For Typescript linting, see [@misk/tslint](https://www.npmjs.com/package/@misk/tslint).

Getting Started
---
```bash
$ yarn add @misk/dev
```

TsConfig Template
---
Create a `tsconfig.json` file in the repo root directory with the following:

```JSON
  {
    "extends": "./node_modules/@misk/dev/tsconfig.base",
    "compilerOptions": {
        "outDir": "./dist"
    }
  }
```

Webpack Template
---
Create a `webpack.config.js` file in the repo root directory with the following:

```Javascript
const { MiskWebpackConfigBase } = require("@misk/webpack")
const path = require('path')
const miskTabWebpack = require(path.join(process.cwd(), "package.json")).miskTabWebpack
module.exports = MiskWebpackConfigBase(process.env.NODE_ENV, {
  "dirname": __dirname,
  miskTabWebpack,
})
```

Package.json Input Parameters
---
The child Webpack template above consumes some static initialization variables that you must add to your `package.json`. An example for the Config tab is included below.

Note that the `output_path` must match the `outDir` specified in the repo's `tsconfig.json`.

```JSON
  "devDependencies": {
    "@misk/dev": "^0.0.20"
  },
  "miskTabWebpack": {
    "name": "Config",
    "output_path": "dist",
    "port": "3000",
    "relative_path_prefix": "_tab/config/",
    "slug": "config"
  }
```

Included Libraries
---
```JSON
  "@types/node": "^10.9.4",
  "@types/react": "^16.4.13",
  "@types/react-dom": "^16.0.7",
  "@types/react-helmet": "^5.0.7",
  "@types/react-hot-loader": "^4.1.0",
  "@types/react-redux": "^6.0.6",
  "@types/react-router": "^4.0.30",
  "@types/react-router-dom": "^4.3.0",
  "@types/react-router-redux": "^5.0.15",
  "@types/webpack": "^4.4.11",
  "@types/webpack-env": "^1.13.6",
  "awesome-typescript-loader": "^5.2.0",
  "copy-webpack-plugin": "^4.5.2",
  "cross-env": "^5.2.0",
  "css-loader": "^1.0.0",
  "file-loader": "^2.0.0",
  "html-webpack-plugin": "^3.2.0",
  "node-sass": "^4.9.3",
  "sass-loader": "^7.1.0",
  "source-map-loader": "^0.2.4",
  "style-loader": "^0.23.0",
  "typescript": "^3.0.3",
  "webpack": "^4.17.2",
  "webpack-cli": "^3.1.0",
  "webpack-dev-server": "^3.1.7",
  "webpack-merge": "^4.1.4"
```

[Releasing](https://github.com/square/misk/blob/master/misk/web/%40misk/RELEASING.md)
---