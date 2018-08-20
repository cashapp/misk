Misk Tabs
---
![](https://raw.githubusercontent.com/square/misk/master/misk.png)
This package provides Webpack externals for Misk tabs exported in the format `@misktabs/{unique tabname}`.

Getting Started
---
```bash
$ yarn add @misk/tabs
```

- Use `@misk/tabs` externals to give access in Webpack repos to tabs libraries not locally installed. Add the following to your `webpack.config.js`.
  
  ```Typescript
  const MiskCommon = require('@misk/common')
  const MiskTabs = require('@misk/tabs')

  ...

  module.exports = {
    mode
    entry
    ...
    externals: { ...MiskCommon.externals, ...MiskTabs.externals },
  }

  ```

Tabs
---
- `@misktabs/config`

Contributing
---
#Adding a new tab
- Add package to window variable mapping to `src/externals.ts`
- Update `README.md` with a copy of the updated `package.json` list of packages

[Releasing](https://github.com/square/misk/blob/master/misk/web/%40misk/RELEASING.md)
---