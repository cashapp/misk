const miskCommon = require('../../@misk/common/lib');
const path = require('path');

module.exports = {
  mode: 'development',
  entry: {
    'config': path.resolve(__dirname, 'src/core/index.ts'),
    'configMenu': path.resolve(__dirname, 'src/mainMenu/index.ts'),

  },
  devtool: 'cheap-module-eval-source-map',
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
  externals: miskCommon.externals
};
