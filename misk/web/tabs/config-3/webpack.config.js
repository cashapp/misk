const path = require('path');
const MiskCommon = require('@misk/common');

module.exports = {
  mode: 'production',
  entry: {
    tab_config: path.resolve(__dirname, 'src/index.ts')
  },
  devtool: 'source-map',
  output: {
    filename: '_tab/config/tab_config.js',
    path: path.join(__dirname, 'dist'),
    publicPath: "/",
    library: ['MiskTabs', 'Config'],
    libraryTarget: 'umd',
    /**
     * library will try to bind to browser `window` variable
     * without below globalObject: library binding to browser `window` 
     *    fails when run in Node or other non-browser
     */
    globalObject: 'typeof self !== \'undefined\' ? self : this'
  },
  devServer: {
    port: '3200',
    inline: true,
    historyApiFallback: true
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
  externals: { ...MiskCommon.externals }
};