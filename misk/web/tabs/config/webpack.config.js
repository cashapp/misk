const path = require('path')
const webpack = require('webpack')
const HTMLWebpackPlugin = require('html-webpack-plugin')
const miskCommon = require('@misk/common');

const miskCommonExternals = [{
  "@blueprintjs/core": {
    amd: "@blueprintjs/core",
    commonjs: "@blueprintjs/core",
    commonjs2: "@blueprintjs/core",
    root: "window.BlueprintjsCore",
  }
}, {
  "@blueprintjs/icons": {
    amd: "@blueprintjs/icons",
    commonjs: "@blueprintjs/icons",
    commonjs2: "@blueprintjs/icons",
    root: "window.BlueprintjsIcons",
  }
}, {
  "axios": {
    amd: "axios",
    commonjs: "axios",
    commonjs2: "axios",
    root: "window.Axios",
    },
}, {
  "history": {
    amd: "history",
    commonjs: "history",
    commonjs2: "history",
    root: "window.History",
    },
}, {
  "@misk/common": {
    amd: "@misk/common",
    commonjs: "@misk/common",
    commonjs2: "@misk/common",
    root: "window.MiskCommon",
    },
}, {
  "@misk/components": {
    amd: "@misk/components",
    commonjs: "@misk/components",
    commonjs2: "@misk/components",
    root: "window.MiskComponents",
    },
}, {
  "@misk/dev": {
    amd: "@misk/dev",
    commonjs: "@misk/dev",
    commonjs2: "@misk/dev",
    root: "window.MiskDev",
    },
}, {
  "react": {
    amd: "react",
    commonjs: "react",
    commonjs2: "react",
    root: "window.React",
  },
}, {
  "react-dom": {
    amd: "react-dom",
    commonjs: "react-dom",
    commonjs2: "react-dom",
    root: "window.ReactDOM",
  }
}, {
  "react-helmet": {
    amd: "react-helmet",
    commonjs: "react-helmet",
    commonjs2: "react-helmet",
    root: "window.ReactHelmet",
  },
}, {
  "react-hot-loader": {
    amd: "react-hot-loader",
    commonjs: "react-hot-loader",
    commonjs2: "react-hot-loader",
    root: "window.ReactHotLoader",
    },
}, {
  "react-redux": {
    amd: "react-redux",
    commonjs: "react-redux",
    commonjs2: "react-redux",
    root: "window.ReactRedux",
    },
}, {
  "react-router": {
    amd: "react-router",
    commonjs: "react-router",
    commonjs2: "react-router",
    root: "ReactRouter",
    },
}, {
  "react-router-dom": {
    amd: "react-router-dom",
    commonjs: "react-router-dom",
    commonjs2: "react-router-dom",
    root: "window.ReactRouterDom",
    },
}, {
  "redux": {
    amd: "redux",
    commonjs: "redux",
    commonjs2: "redux",
    root: "window.Redux",
    },
}, {
  "styled-components": {
    amd: "styled-components",
    commonjs: "styled-components",
    commonjs2: "styled-components",
    root: "window.StyledComponents",
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
    filename: 'tab_config.js',
    path: path.join(__dirname, 'dist/_admin/config'),
    publicPath: "/_admin/config/"
  },
  devServer: {
    port: '3200',
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
      new webpack.HotModuleReplacementPlugin()
    ]
    : [HTMLWebpackPluginConfig, DefinePluginConfig],
  externals: miskCommonExternals
}
