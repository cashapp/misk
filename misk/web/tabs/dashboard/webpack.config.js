var BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin;
const path = require('path')
const webpack = require('webpack')
const HTMLWebpackPlugin = require('html-webpack-plugin')

const miskCommonExternals = [{
  '@blueprintjs/core': {
    root: 'window.BlueprintjsCore',
    commonjs: '@blueprintjs/core',
    commonjs2: '@blueprintjs/core',
    amd: '@blueprintjs/core'
  }
}, {
  '@blueprintjs/icons': {
    root: 'window.BlueprintjsIcons',
    commonjs: '@blueprintjs/icons',
    commonjs2: '@blueprintjs/icons',
    amd: '@blueprintjs/icons'
  }
}, {
  'axios': {
    root: 'window.Axios',
    commonjs: 'axios',
    commonjs2: 'axios',
    amd: 'axios'
    },
}, {
  'history': {
    root: 'window.History',
    commonjs: 'history',
    commonjs2: 'history',
    amd: 'history'
    },
}, {
  '@misk/common': {
    root: 'window.MiskCommon',
    commonjs: '@misk/common',
    commonjs2: '@misk/common',
    amd: '@misk/common'
    },
}, {
  '@misk/components': {
    root: 'window.MiskComponents',
    commonjs: '@misk/components',
    commonjs2: '@misk/components',
    amd: '@misk/components'
    },
}, {
  '@misk/dev': {
    root: 'window.MiskDev',
    commonjs: '@misk/dev',
    commonjs2: '@misk/dev',
    amd: '@misk/dev'
    },
}, {
  'react': {
    root: 'window.React',
    commonjs: 'react',
    commonjs2: 'react',
    amd: 'react',
  },
}, {
  'react-dom': {
    root: 'window.ReactDOM',
    commonjs: 'react-dom',
    commonjs2: 'react-dom',
    amd: 'react-dom'
  }
}, {
  'react-helmet': {
    root: 'window.ReactHelmet',
    commonjs: 'react-helmet',
    commonjs2: 'react-helmet',
    amd: 'react-helmet'
    },
}, {
  'react-hot-loader': {
    root: 'window.ReactHotLoader',
    commonjs: 'react-hot-loader',
    commonjs2: 'react-hot-loader',
    amd: 'react-hot-loader'
    },
}, {
  'react-redux': {
    root: 'window.ReactRedux',
    commonjs: 'react-redux',
    commonjs2: 'react-redux',
    amd: 'react-redux'
    },
}, {
  'react-router': {
    root: 'ReactRouter',
    commonjs: 'react-router',
    commonjs2: 'react-router',
    amd: 'react-router'
    },
}, {
  'react-router-dom': {
    root: 'window.ReactRouterDom',
    commonjs: 'react-router-dom',
    commonjs2: 'react-router-dom',
    amd: 'react-router-dom'
    },
}, {
  'redux': {
    root: 'window.Redux',
    commonjs: 'redux',
    commonjs2: 'redux',
    amd: 'redux'
    },
}, {
  'styled-components': {
    root: 'window.StyledComponents',
    commonjs: 'styled-components',
    commonjs2: 'styled-components',
    amd: 'styled-components'
    },
}]

const dev = process.env.NODE_ENV !== 'production'

const HTMLWebpackPluginConfig = new HTMLWebpackPlugin({
  template: path.join(__dirname, '/src/index.html'),
  filename: 'index.html',
  inject: 'body'
})

const DefinePluginConfig = new webpack.DefinePlugin({
  'process.env.NODE_ENV': JSON.stringify('production')
})

module.exports = {
  entry: ['react-hot-loader/patch', path.join(__dirname, '/src/index.tsx')],
  output: {
    filename: 'tab_dashboard.js',
    path: path.join(__dirname, 'dist/_admin/dashboard'),
    publicPath: "/_admin/dashboard/"
},
  devServer: {
    port: '3110',
    inline: true,
    hot: true,
    historyApiFallback: true
  },
  module: {
    rules: [
      {
        test: /\.(tsx|ts)$/,
        exclude: /node_modules/,
        loaders: 'awesome-typescript-loader'
      },
      {
        enforce: 'pre',
        test: /\.js$/,
        loader: 'source-map-loader'
      },
      {
        test: /\.scss$/,
        loader: 'style-loader!css-loader!sass-loader'
      },
      {
        test: /\.(jpe?g|png|gif|svg)$/i,
        loader: 'url-loader',
        options: {
          limit: 10000
        }
      }
    ]
  },
  resolve: {
    extensions: ['.js', '.jsx', '.ts', '.tsx', '.json']
  },
  mode: dev ? 'development' : 'production',
  plugins: dev
    ? [
      HTMLWebpackPluginConfig,
      new webpack.HotModuleReplacementPlugin(),
      new BundleAnalyzerPlugin({
        analyzerMode: 'static'
      }),
    ]
    : [HTMLWebpackPluginConfig, DefinePluginConfig],
  // externals: miskCommonExternals
}
