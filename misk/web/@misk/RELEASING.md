## Releasing a `@Misk/` Package on NPM

This outlines the steps necessary to release a new NPM package version for any of the `@Misk/` scoped packages in this repo.

## Setup

- Create an NPM user at [npmjs.com](http://npmjs.com/) and request membership in the [`@Misk` organization ](https://www.npmjs.com/org/misk). Membership will give you publish permissions for `@Misk` scoped packages.
- **Note:** member level may not actually be high enough to publish, needs further testing whether publish permissions are only available at Admin or Owner level.
- On your development machine, run `$ npm login` to authorize your local environment with publish permissions

## Releasing

- Each `@Misk/` scoped package in this repo has built in scripts that will clean, run tests, and compile to minimized distributable code
- Increase the package version in `package.json`. Each package increases versions independent of the others (ie. there is no set goal to always have the same version number for every `@Misk/` package)
- Publish the package with `$ npm publish --access=public`

## Package Release Order

- Release packages in the following order given that each are dependent or peer dependent differently on each other:
  - `@Misk/Dev`
  - `@Misk/Common`
  - `@Misk/Components`
- Update the `package.json` for each further package with the newest version of the immedietely previous updated package

## Example Release Flow

- Assume following starting versions for the `@Misk/` packages and consider equivalent in order any package listed above alongside `@Misk/Components`
  - `@Misk/Dev`: 0.0.6
  - `@Misk/Common`: 0.0.12
  - `@Misk/Components`: 0.0.2
- Update all minor versions of `@Misk/` packages in `package.json`
- Publish new version 0.0.7 for `@Misk/Dev`
- Update `package.json` in `@Misk/Common` and `@Misk/Components` to bump `@Misk/Dev` to version 0.0.7
- Reinstall node modules and test that they work with the new `@Misk/Dev` package version `rm -rf node_modules/; yarn install`
- Publish new version 0.0.13 for `@Misk/Common`
- Update `package.json` in `@Misk/Components` to bump `@Misk/Common` to version 0.0.13
- Reinstall node modules and test that they work with the new `@Misk/Dev` and `@Misk/Common` package version `rm -rf node_modules/; yarn install`
- Publish new version 0.0.3 for `@Misk/Components`
- Update all related tabs to the newest `@Misk/` packages

## Deprecating

If a package is no longer required, you must mark it as deprecated on NPM. Use the command below with an informative message and your One Time Password for NPM 2FA.

```Bash
npm deprecate @misk/tabs@0.0.1 "Deprecation Message" --otp=
```
