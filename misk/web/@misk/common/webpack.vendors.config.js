const path = require('path');

module.exports = {
  name: "static",
  mode: 'production',
  entry: {
    vendors: path.resolve(__dirname, 'src/vendors.js')
  },
  devtool: 'source-map',
  output: {
    path: path.resolve(__dirname, './lib/static'),
    filename: '[name].js',
    library: ['misk', 'common'],
    libraryTarget: 'umd',
    globalObject: 'typeof window !== \'undefined\' ? window : this'
  },
};