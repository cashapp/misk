const path = require('path');

module.exports = {
  name: "static",
  mode: 'production',
  entry: {
    styles: path.resolve(__dirname, 'src/styles.js')
  },
  devtool: 'source-map',
  output: {
    path: path.resolve(__dirname, './lib/web/@misk/common'),
    filename: '[name].js'
  },
  module: {
    rules: [
      {
        test: /\.(scss|sass|css)$/,
        loader: ['style-loader', 'css-loader?minimize=true', 'sass-loader']
      }
    ]
  },
};