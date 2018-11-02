# Misk Web

## Installing the Admin Dashboard on a new service

For the purpose of this guide, the service will be called DinoService.

1. Install `MiskWebModule` and `AdminDashboardModule` in `DinoServiceModule`

```Kotlin
class DinoServiceModule : KAbstractModule() {
  override fun configure() {
    val environment = Environment.fromEnvironmentVariable()
    install(DinoModule(environment))

    install(MiskWebModule())
    install(AdminDashboardModule(environment, "Dino", "/_admin/"))

    ...
  }
}
```

# Other Notes

## Organization

- `@misk`: all npm packages
  - `@misk/common`: common dependencies
  - `@misk/components`: reusable React components
  - `@misk/dev`: common devDependencies, TsConfig, Webpack build configs, Webpack externals
  - `@misk/tslint`: common TsLint configs
- `tabs`: all modular parts of the dashboard
  - `config`: config tab
  - `loader`: thin wrapper that has the main router, all script tags, handles hide/show each tab when it's clicked
  - ... to be built:
  - `webActions`: show all installed `WebActions`
  - `guice`: to be built
  - `hibernate`: to be built
  - `healthcheck`: to be built

## Framework and Languages

- Typescript + ReactJS
- [Blueprintjs](http://blueprintjs.com/) for UI elements + Typescript compatible Icons
- [Styled Components](https://www.styled-components.com/) for adding CSS to React components
- [Skeleton](http://getskeleton.com/) for very simple responsive boilerplate styling

## DevEnv Setup

- Install IntelliJ Ultimate and [Setup Typescript Support](https://www.jetbrains.com/help/idea/2017.1/typescript-support.html). Typescript errors will now appear as you type.
- For a more native React/Typescript environment, install Microsoft Visual Studio Code and the following packages
  - Blueprint: Syntax theme that includes highlighting of BlueprintJS elements
  - Prettier - Code formatter
  - stylelint: CSS/SCSS/Less linter
  - TSLint: provides linting recommendations and auto-fixes within the editor for Typescript
- Duplicate the Example tab and follow the instructions in [tabs/README.md](./tabs/README.md).

## Typescript Nuances

- Some NPM packages are written in Typescript, these are very easy to import and use without issue.
- Some NPM packages are **not** written in Typescript but include Typescript typings `filename.d.ts` files to allow use in Typescript projects.
- Some NPM packages do **not** have Typescript included typings but have community contributed ones in a NPM `@types/package-name` library. Check if it exists in the [DefinitelyTyped](https://github.com/DefinitelyTyped/DefinitelyTyped) repo.
- Some NPM packages have none of the above, are in pure JS, and you're out of luck. If there are alternative packages that fit into the three above categories, that is highly preferable so full Typescript safety checking can be ensured. If there are none, then the following methods will allow JS libraries to work:
  - You can be a good OSS citizen and add typings files to the packages you want to use.
  - Override Typescript using require imports `const module = require('package-name');` instead of es6 standard imports `import { module } from 'package-name';`.

## Assumptions

- All modules and code are being run on network internal environments by authenticated employees
  - Thus: production webpack does output `source-map` for easier debugging of any production errors. If this framework were to be used outside of a trusted environment, then that should be removed since it does expose source code.

## Resources

- [Primary Strategy](https://stackoverflow.com/questions/44778265/dynamically-loading-react-components)
- [Dynamic Loading React Components](https://www.slightedgecoder.com/2017/12/03/loading-react-components-dynamically-demand/)
- [Webpack: export to existing module in window](https://stackoverflow.com/questions/30539725/webpack-export-to-existing-module-in-window?utm_medium=organic&utm_source=google_rich_qa&utm_campaign=google_rich_qa)

## Running in Production

- Run ShadowJar Gradle task in Intellij for urlshortener
- `$ java -jar {urlshortener jar path}`

## GitIgnore

Add these to your `.gitignore` and you will be happy

```
.DS_Store
.git
**/@misk/web
cachedUrls
logs
*.log
npm-debug.log*
coverage
node_modules
.npm
build
lib
dist
.hash
```
