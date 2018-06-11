export const externals = [
{
  'lodash': {
    root: '_',
    commonjs: 'lodash',
    commonjs2: 'lodash',
    amd: 'lodash',
  },
}, {
  'rxjs': {
    root: 'Rx',
    commonjs: 'rxjs',
    commonjs2: 'rxjs',
    amd: 'rxjs',
  },
}, {
  'react': {
    root: 'React',
    commonjs: 'react',
    commonjs2: 'react',
    amd: 'react',
  },
}, {
  'react-dom': {
    root: 'ReactDOM',
    commonjs: 'react-dom',
    commonjs2: 'react-dom',
    amd: 'react-dom'
  }
}, {
  'prop-types': {
    root: 'PropTypes',
    commonjs: 'prop-types',
    commonjs2: 'prop-types',
    amd: 'prop-types',
  }
}, {
  'frint': {
    root: 'Frint',
    commonjs: 'frint',
    commonjs2: 'frint',
    amd: 'frint'
  },
}, {
  'frint-store': {
    root: 'FrintStore',
    commonjs: 'frint-store',
    commonjs2: 'frint-store',
    amd: 'frint-store'
  },
}, {
  'frint-react': {
    root: 'FrintReact',
    commonjs: 'frint-react',
    commonjs2: 'frint-react',
    amd: 'frint-react'
  },
}, {
  'frint-router': {
    root: 'FrintRouter',
    commonjs: 'frint-router',
    commonjs2: 'frint-router',
    amd: 'frint-router'
  },
}, {
  'frint-router-react': {
    root: 'FrintRouterReact',
    commonjs: 'frint-router-react',
    commonjs2: 'frint-router-react',
    amd: 'frint-router-react'
  }
}, {
  'frint-router/BrowserRouterService': {
    root: 'FrintRouterBrowserRouterService',
    commonjs: 'frint-router/BrowserRouterService',
    commonjs2: 'frint-router/BrowserRouterService',
    amd: 'frint-router/BrowserRouterService'
  }
}, {
  '@blueprintjs/core': {
    root: 'BlueprintjsCore',
    commonjs: '@blueprintjs/core',
    commonjs2: '@blueprintjs/core',
    amd: '@blueprintjs/core'
  }
}, {
  'react-transition-group': {
    root: 'ReactTransitionGroup',
    commonjs: 'react-transition-group',
    commonjs2: 'react-transition-group',
    amd: 'react-transition-group'
  }
}
];

// concat(config.lodashExternals)
// .concat(config.rxjsExternals)
// .concat(config.thirdPartyExternals)
// .concat(config.frintExternals)
// .concat(miskConfig.miskExternals)

// add frint-config as req to this so it exports complete externals list and then frint-config not required in main/modules
// also create @misk/dev ||OR|| add to this repo all core dev dependencies

export default externals;