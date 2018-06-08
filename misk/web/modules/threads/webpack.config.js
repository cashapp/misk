const HtmlWebpackPlugin = require('html-webpack-plugin');
const ScriptExtHtmlWebpackPlugin = require('script-ext-html-webpack-plugin');
const config = require('frint-config');
const miskConfig = require('../../misk-lib');
const path = require('path');

module.exports = {
  entry: {
    'threads': path.resolve(__dirname, 'src/core/index.ts'),
    'threadsMenu': path.resolve(__dirname, 'src/mainMenu/index.ts'),

  },
  devtool: 'source-map',
  output: {
    path: path.resolve(__dirname, '../../main/build/js'),
    filename: '[name].js',
    libraryTarget: 'umd'
  },
  module: {
    rules: [
      {
        test: /\.js$/,
        exclude: /(node_modules)/,
        loader: 'babel-loader',
      }, { 
        test: /\.tsx?$/, 
        loader: "awesome-typescript-loader" 
      }, { 
        enforce: "pre", 
        test: /\.js$/, 
        loader: "source-map-loader" 
      }, {
        test: /\.(scss|sass|css)$/,
        loader: ['style-loader', 'css-loader', 'sass-loader']
      },
      {
        test: /\.(png|jpg|gif|svg)$/,
        loader: ['file-loader']
      }
    ]
  },
  resolve: {
    extensions: [".ts", ".tsx", ".js", ".jsx", ".json"]
  },
  externals: []
    .concat(config.lodashExternals)
    .concat(config.rxjsExternals)
    .concat(config.thirdPartyExternals)
    .concat(config.frintExternals)
    .concat(miskConfig.miskExternals)
};
