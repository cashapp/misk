Misk TsLint
---
![](https://raw.githubusercontent.com/square/misk/master/misk.png)
This package provides shared TsLint linting across Misk tab repos.

Getting Started
---
```bash
$ yarn add @misk/tslint
```

TsLint Template
---
Create a `tslint.json` file in the repo root directory with the following:

```JSON
  {
    "extends": "@misk/tslint"
  }
```

Included TsLint Packages
---
```JSON
  "tslint": "^5.11.0",
  "tslint-blueprint": "^0.1.0",
  "tslint-clean-code": "^0.2.7",
  "tslint-config-prettier": "^1.15.0",
  "tslint-consistent-codestyle": "^1.13.3",
  "tslint-eslint-rules": "^5.4.0",
  "tslint-immutable": "^4.7.0",
  "tslint-react": "^3.6.0",
  "tslint-sonarts": "^1.7.0"
```

[Releasing](https://github.com/square/misk/blob/master/misk/web/%40misk/RELEASING.md)
---