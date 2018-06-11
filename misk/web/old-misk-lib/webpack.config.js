const path = require('path');

module.exports = {
  entry: {
    miskConfig: path.resolve(__dirname, 'index.js'),
    styles: path.resolve(__dirname, 'styles.js'),
    vendor: path.resolve(__dirname, 'vendor.js')
  },
  devtool: 'source-map',
  output: {
    path: path.resolve(__dirname, '../main/build/js'),
    filename: '[name].js'
  },
  module: {
    rules: [
      {
        test: /\.(scss|sass|css)$/,
        loader: ['style-loader', 'css-loader', 'sass-loader']
      }
    ]
  },
};