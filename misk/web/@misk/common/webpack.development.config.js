const path = require('path');

module.exports = {
  entry: {
    miskConfig: path.resolve(__dirname, 'src/index.ts'),
    styles: path.resolve(__dirname, 'src/styles.ts'),
    vendor: path.resolve(__dirname, 'src/vendor.ts')
  },
  devtool: 'source-map',
  output: {
    path: path.resolve(__dirname, '../../main/build/js'),
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