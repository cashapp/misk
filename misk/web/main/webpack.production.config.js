const HtmlWebpackPlugin = require('html-webpack-plugin');
const ScriptExtHtmlWebpackPlugin = require('script-ext-html-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const miskCommon = require('../@misk/common/lib');
const path = require('path');

module.exports = {
  mode: 'production',
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
        loader: 'awesome-typescript-loader'
      }, { 
        enforce: 'pre', 
        test: /\.js$/, 
        loader: 'source-map-loader'
      }, {
        test: /\.(scss|sass|css)$/,
        loader: ['style-loader', 'css-loader?minimize=true', 'sass-loader']
      }, {
        test: /\.(png|jpg|gif|svg)$/,
        loader: 'file-loader'
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
    }),
    new CopyWebpackPlugin(
      [{ from: './src/public', to: '../' },
      { from: './node_modules/@misk/common/lib' }], 
      { debug: 'info', copyUnmodified: true }
    )
  ],
  externals: miskCommon.externals,
};
