Misk Typescript
---
![](https://raw.githubusercontent.com/square/misk/master/misk.png)
This package provides shared compiler options and linting used to provide a common Typescript environment across Misk tab repos.

Getting Started
---
```bash
$ yarn add @misk/typescript
```

TsConfig Template
---
Create a `tsconfig.json` file in the repo root directory with the following:

```JSON
  {
    "extends": "./node_modules/@misk/typescript/tsconfig.base",
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
    "extends": "@misk/typescript"
  }
```

[Releasing](https://github.com/square/misk/blob/master/misk/web/%40misk/RELEASING.md)
---