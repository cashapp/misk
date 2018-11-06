## Misk Dev

![](https://raw.githubusercontent.com/square/misk/master/misk.png)
This package provides shared devDependencies and compiler options used to provide a common development environment across Misk tab repos. For Typescript linting, see [@misk/tslint](https://www.npmjs.com/package/@misk/tslint).

## Getting Started

```bash
$ yarn add @misk/dev
```

## TsConfig Template

Create a `tsconfig.json` file in the repo root directory with the following:

```JSON
  {
    "extends": "../../node_modules/@misk/dev/tsconfig.base",
    "compilerOptions": {
      "outDir": "./lib/web/_tab/{tabname}"
    }
  }
```

## Prettier Config

Create a `prettier.config.js` file in the repo root directory with the following:

```Javascript
const { createPrettierConfig } = require("@misk/dev")
module.exports = createPrettierConfig()
```

## Webpack Template

Create a `webpack.config.js` file in the repo root directory with the following:

```Javascript
const { createTabWebpack } = require("@misk/dev")
const path = require('path')
const miskTab = require(path.join(process.cwd(), "package.json")).miskTab
module.exports = createTabWebpack(process.env.NODE_ENV, {
  "dirname": __dirname,
  miskTab
},
{
  // optional: any other Webpack config fields to be merged with the Misk Tab Webpack Config
})
```

## Webpack Externals

Used in `createTabWebpack` but also available as an export of `@misk/dev` are the following externals objects that are formatted for use by Webpack to exclude libraries from compiled code:

- `vendorExternals`: vendor libraries included in `@misk/common/lib/vendors.js`
- `miskExternals`: all Misk libraries

If you are using one of the Webpack builders in `@misk/dev`, all externals above are included in the Webpack config. To use the externals in other Webpack configs, follow the steps below.

- Add the following to your `webpack.config.js` as relevant.

  ```Javascript
  const MiskDev = require('@misk/dev')

  ...

  module.exports = {
    mode
    entry
    ...
    externals: { ...MiskDev.vendorExternals, ...MiskDev.miskExternals },
  }
  ```

To build your own externals, use the exported function `createExternals` which consumes an object of the below structure which maps a key (NPM package name) to a normalized library name that will be mounted on browser `window`.

```JSON
{
  "@blueprintjs/core": ["Blueprint", "Core"],
  "@blueprintjs/icons": ["Blueprint", "Icons"],
  "axios": "Axios",
  ...
}
```

## VSCode

Included are recommended extensions and settings.

Add settings by copying the JSON from the file into `.vscode/settings.json` in your Tab repo.

## Package.json Input Parameters

The child Webpack template above consumes some static initialization variables that you must add to your `package.json`. An example for the Config tab is included below.

Note that the `output_path` must match the `outDir` specified in the repo's `tsconfig.json`.

```JSON
  "devDependencies": {
    "@misk/dev": "^0.0.20"
  },
  "miskTab": {
    "name": "Config",
    "output_path": "dist",
    "port": "3000",
    "relative_path_prefix": "_tab/config/",
    "slug": "config"
  }
```

Included Libraries: Look at `package.json`

## [Releasing](https://github.com/square/misk/blob/master/misk/web/%40misk/RELEASING.md)

## [Changelog (and Breaking Changes)](https://github.com/square/misk/blob/master/misk/web/%40misk/CHANGELOG.md)
