Misk Dev
---
![](https://raw.githubusercontent.com/square/misk/master/misk.png)
This package provides shared devDependencies, compiler options, and linting used to provide a common development environment across Misk tab repos.

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

TsLint Template
---
Create a `tslint.json` file in the repo root directory with the following:

```JSON
  {
    "extends": "@misk/dev"
  }
```

Included Libraries
---
```JSON
  "@types/node": "^10.7.0",
  "@types/react": "^16.4.10",
  "@types/react-dom": "^16.0.7",
  "@types/react-helmet": "^5.0.7",
  "@types/react-hot-loader": "^4.1.0",
  "@types/react-redux": "^6.0.6",
  "@types/react-router": "^4.0.30",
  "@types/react-router-dom": "^4.3.0",
  "@types/react-router-redux": "^5.0.15",
  "@types/webpack-env": "^1.13.6",
  "tslint": "^5.11.0",
  "tslint-blueprint": "^0.1.0",
  "tslint-clean-code": "^0.2.7",
  "tslint-config-prettier": "^1.14.0",
  "tslint-consistent-codestyle": "^1.13.3",
  "tslint-eslint-rules": "^5.3.1",
  "tslint-immutable": "^4.6.0",
  "tslint-react": "^3.6.0",
  "tslint-sonarts": "^1.7.0",
  "typescript": "^3.0.1"
```

[Releasing](https://github.com/square/misk/blob/master/misk/web/%40misk/RELEASING.md)
---