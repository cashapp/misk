const path = require('path');

module.exports = {
  name: "vendors",
  mode: 'production',
  entry: {
    vendors: path.resolve(__dirname, 'src/vendors.js')
  },
  devtool: 'source-map',
  output: {
    path: path.resolve(__dirname, './lib'),
    filename: '[name].js',
    library: ['Misk', 'Common'],
    libraryTarget: 'umd',
    globalObject: 'typeof window !== \'undefined\' ? window : this'
  },
};