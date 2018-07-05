const path = require('path')
const webpack = require('webpack')
const HTMLWebpackPlugin = require('html-webpack-plugin')
const miskCommon = require('@misk/common');

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
    externals: miskCommon.externals
}
