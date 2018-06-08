export const miskExternals = [{
  '@blueprintjs/core': {
    root: 'BlueprintjsCore',
    commonjs: '@blueprintjs/core',
    commonjs2: '@blueprintjs/core',
    amd: '@blueprintjs/core'
  }
},
{
  '@fortawesome/fontawesome': {
    root: 'FontAwesome',
    commonjs: '@fortawesome/fontawesome',
    commonjs2: '@fortawesome/fontawesome',
    amd: '@fortawesome/fontawesome'
  }
},
{
  '@fortawesome/fontawesome-free-brands': {
    root: 'FontAwesomeBrands',
    commonjs: '@fortawesome/fontawesome-free-brands',
    commonjs2: '@fortawesome/fontawesome-free-brands',
    amd: '@fortawesome/fontawesome-free-brands'
  }
},
{
  '@fortawesome/fontawesome-free-regular': {
    root: 'FontAwesomeRegular',
    commonjs: '@fortawesome/fontawesome-free-regular',
    commonjs2: '@fortawesome/fontawesome-free-regular',
    amd: '@fortawesome/fontawesome-free-regular'
  }
},
{
  '@fortawesome/fontawesome-free-solid': {
    root: 'FontAwesomeSolid',
    commonjs: '@fortawesome/fontawesome-free-solid',
    commonjs2: '@fortawesome/fontawesome-free-solid',
    amd: '@fortawesome/fontawesome-free-solid'
  }
},
];

// "@fortawesome/fontawesome": "^1.1.8",
// "@fortawesome/fontawesome-free-brands": "^5.0.13",
// "@fortawesome/fontawesome-free-regular": "^5.0.13",
// "@fortawesome/fontawesome-free-solid": "^5.0.13",


// concat(config.lodashExternals)
// .concat(config.rxjsExternals)
// .concat(config.thirdPartyExternals)
// .concat(config.frintExternals)
// .concat(miskConfig.miskExternals)


// add frint-config as req to this so it exports complete externals list and then frint-config not required in main/modules
// also create @misk/dev ||OR|| add to this repo all core dev dependencies
export default miskExternals;