const path = require('path')
const webpack = require('webpack')
const CopyWebpackPlugin = require('copy-webpack-plugin')
const HTMLWebpackPlugin = require('html-webpack-plugin')
const HtmlWebpackExternalsPlugin = require('html-webpack-externals-plugin')
const miskCommon = require('@misk/common')

const miskCommonExternals = {
  '@blueprintjs/core':
   { amd: '@blueprintjs/core',
     commonjs: '@blueprintjs/core',
     commonjs2: '@blueprintjs/core',
     root: ['Blueprint', 'Core'] },
  '@blueprintjs/icons':
   { amd: '@blueprintjs/icons',
     commonjs: '@blueprintjs/icons',
     commonjs2: '@blueprintjs/icons',
     root: ['Blueprint', 'Icon'] },
    axios:
      { amd: 'axios',
        commonjs: 'axios',
        commonjs2: 'axios',
        root: 'Axios' },
    "connected-react-router": 
    {
      amd: 'connected-react-router',
      commonjs: 'connected-react-router',
      commonjs2: 'connected-react-router',
      root: 'ConnectedReactRouter'
    },
     history:
      { amd: 'history',
        commonjs: 'history',
        commonjs2: 'history',
        root: 'HistoryNPM' },
     'react-dom':
      { amd: 'react-dom',
        commonjs: 'react-dom',
        commonjs2: 'react-dom',
        root: 'ReactDom' },
     'react-helmet':
      { amd: 'react-helmet',
        commonjs: 'react-helmet',
        commonjs2: 'react-helmet',
        root: 'ReactHelmet' },
     'react-hot-loader':
      { amd: 'react-hot-loader',
        commonjs: 'react-hot-loader',
        commonjs2: 'react-hot-loader',
        root: 'ReactHotLoader' },
     'react-redux':
      { amd: 'react-redux',
        commonjs: 'react-redux',
        commonjs2: 'react-redux',
        root: 'ReactRedux' },
     'react-router':
      { amd: 'react-router',
        commonjs: 'react-router',
        commonjs2: 'react-router',
        root: 'ReactRouter' },
     'react-router-dom':
      { amd: 'react-router-dom',
        commonjs: 'react-router-dom',
        commonjs2: 'react-router-dom',
        root: 'ReactRouterDom' },
     redux:
      { amd: 'redux',
        commonjs: 'redux',
        commonjs2: 'redux',
        root: 'Redux' },
    //  'styled-components':
    //   { amd: 'styled-components',
    //     commonjs: 'styled-components',
    //     commonjs2: 'styled-components',
    //     root: 'StyledComponents' } 
  }


const dev = process.env.NODE_ENV !== 'production'

const HTMLWebpackPluginConfig = new HTMLWebpackPlugin({
  template: path.join(__dirname, '/src/index.html'),
  filename: 'index.html',
  inject: 'body'
})

const DefinePluginConfig = new webpack.DefinePlugin({
  'process.env.NODE_ENV': JSON.stringify('production')
})

const CopyWebpackPluginConfig = new CopyWebpackPlugin(
  [{ from: './node_modules/@misk/common/lib', to: '@misk/common/lib'}], 
  { debug: 'info', copyUnmodified: true }
)

// const HTMLExternalsPluginConfig = new HtmlWebpackExternalsPlugin({
//   externals: miskCommon.externals
// })

module.exports = {
  // entry: ['react-hot-loader/patch', path.join(__dirname, '/src/index.tsx')],
  entry: path.join(__dirname, '/src/index.tsx'),
  output: {
    filename: 'tab_dashboard.js',
    path: path.join(__dirname, 'dist/_admin/dashboard'),
    publicPath: "/_admin/dashboard/"
  },
  devServer: {
    port: '3110',
    inline: true,
    // hot: true,
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
      HTMLWebpackPluginConfig, CopyWebpackPluginConfig, 
      // HTMLExternalsPluginConfig,
      // new webpack.HotModuleReplacementPlugin(),
    ]
    : [HTMLWebpackPluginConfig, CopyWebpackPluginConfig, 
      // HTMLExternalsPluginConfig,
       DefinePluginConfig],
  // externals: miskCommonExternals
  externals: miskCommon.externals
}
