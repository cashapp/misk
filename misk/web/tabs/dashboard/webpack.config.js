const path = require('path')
const webpack = require('webpack')
const CopyWebpackPlugin = require('copy-webpack-plugin')
const HTMLWebpackPlugin = require('html-webpack-plugin')
const MiskCommon = require('@misk/common')
const MiskTabs = require('@misk/tabs')

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
  [
    { from: './node_modules/@misk/common/lib', to: '_tab/dashboard/@misk/'},
    { from: './node_modules/@misk/components/lib', to: '_tab/dashboard/@misk/'}
  ], 
  { debug: 'info', copyUnmodified: true }
)

module.exports = {
  entry: ['react-hot-loader/patch', path.join(__dirname, '/src/index.tsx')],
  output: {
    filename: '_tab/dashboard/tab_dashboard.js',
    path: path.join(__dirname, 'dist'),
    publicPath: "/",
    library: ['MiskTabs', 'Dashboard'],
    libraryTarget: 'umd',
    /**
     * library will try to bind to browser `window` variable
     * without below globalObject: library binding to browser `window` 
     *    fails when run in Node or other non-browser
     */
    globalObject: 'typeof self !== \'undefined\' ? self : this'
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
      HTMLWebpackPluginConfig, CopyWebpackPluginConfig, 
      new webpack.HotModuleReplacementPlugin(),
    ]
    : [HTMLWebpackPluginConfig, CopyWebpackPluginConfig, 
      DefinePluginConfig],
  externals: { ...MiskCommon.externals, ...MiskTabs.externals }
}
