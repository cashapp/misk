## Misk TsLint

![](https://raw.githubusercontent.com/square/misk/master/misk.png)
This package provides shared TsLint linting across Misk tab repos.

## Getting Started

```bash
$ yarn add @misk/tslint
```

## TsLint Template

Create a `tslint.json` file in the repo root directory with the following:

```JSON
  {
    "extends": "@misk/tslint"
  }
```

## Included TsLint Packages

From `package.json`:

```JSON
  tslint
  tslint-blueprint
  tslint-clean-code
  tslint-config-prettier
  tslint-consistent-codestyle
  tslint-eslint-rules
  tslint-immutable
  tslint-react
  tslint-sonarts
```

## [Releasing](https://github.com/square/misk/blob/master/misk/web/%40misk/RELEASING.md)

## [Changelog (and Breaking Changes)](https://github.com/square/misk/blob/master/misk/web/%40misk/CHANGELOG.md)
