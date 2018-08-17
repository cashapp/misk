const path = require('path');
const MiskCommon = require('@misk/common');

module.exports = {
  mode: 'production',
  entry: {
    components: path.resolve(__dirname, 'src/index.ts')
  },
  devtool: 'source-map',
  output: {
    path: path.resolve(__dirname, './lib'),
    filename: '[name].js',
    library: ['Misk', 'Components'],
    libraryTarget: 'umd',
    /**
     * library will try to bind to browser `window` variable
     * without below globalObject: library binding to browser `window` 
     *    fails when run in Node or other non-browser
     */
    globalObject: 'typeof self !== \'undefined\' ? self : this'
  },
  module: {
    rules: [
      { 
        test: /\.tsx?$/, 
        loader: 'awesome-typescript-loader'
      }, {
        test: /\.(scss|sass|css)$/,
        loader: ['style-loader', 'css-loader?minimize=true', 'sass-loader']
      }
    ]
  },
  resolve: {
    extensions: [".ts", ".tsx", ".js", ".jsx", ".json"]
  },
  externals: MiskCommon.externals
};