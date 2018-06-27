const miskCommon = require('@misk/common');
const path = require('path');

module.exports = {
  mode: 'production',
  entry: {
    'module_config': path.resolve(__dirname, 'src/core/index.ts'),
    'menu_config': path.resolve(__dirname, 'src/mainMenu/index.ts'),
  },
  devtool: 'source-map',
  output: {
    path: path.resolve(__dirname, '../../build/js'),
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
  externals: miskCommon.externals
};
