const HtmlWebpackPlugin = require('html-webpack-plugin');
const ScriptExtHtmlWebpackPlugin = require('script-ext-html-webpack-plugin');
const config = require('frint-config');
const miskConfig = require('../misk-lib');
const path = require('path');

module.exports = {
  entry: {
    core: path.resolve(__dirname, 'src/core/index.ts'),
  },
  devtool: 'source-map',
  output: {
    path: path.resolve(__dirname, './build/js'),
    filename: '[name].js',
    libraryTarget: 'umd'
  },
  devServer: {
    contentBase: path.resolve(__dirname, './build'),
    hot: true,
    inline: true,
    port: 3000,
    historyApiFallback: true,
    compress: true,
    open: true
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
  plugins: [
    new HtmlWebpackPlugin({
      template: path.resolve(__dirname, 'src/layouts/index.ejs'),
      filename: path.resolve(__dirname, './build/index.html'),
    }),
    new ScriptExtHtmlWebpackPlugin({
      defaultAttribute: 'async',
    })
  ],
  externals: []
    .concat(config.lodashExternals)
    .concat(config.rxjsExternals)
    .concat(config.thirdPartyExternals)
    .concat(config.frintExternals)
    .concat(miskConfig.miskExternals)
};
