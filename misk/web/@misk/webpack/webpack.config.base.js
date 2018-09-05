const MiskCommon = require('@misk/common')
const webpack = require('webpack')

const dev = process.env.NODE_ENV !== 'production'

const DefinePluginConfig = new webpack.DefinePlugin({
  'process.env.NODE_ENV': JSON.stringify('production')
})

module.exports = {
  // entry: { defined in child webpack config }
  output: {
    // filename: { defined in child webpack config }
    // path: { defined in child webpack config }
    publicPath: "/",
    // library: { defined in child webpack config }
    libraryTarget: 'umd',
    /**
     * library will try to bind to browser `window` variable
     * without below globalObject: library binding to browser `window` 
     *    fails when run in Node or other non-browser
     */
    globalObject: 'typeof self !== \'undefined\' ? self : this'
  },
  devServer: {
    // port:  { defined in child webpack config }
    inline: true,
    hot: true,
    historyApiFallback: true
  },
  module: {
    rules: [
      {
        test: /\.(tsx|ts)$/,
        exclude: /node_modules/,
        loaders: 'awesome-typescript-loader'
      },
      {
        enforce: 'pre',
        test: /\.js$/,
        loader: 'source-map-loader'
      },
      {
        test: /\.scss$/,
        loader: 'style-loader!css-loader!sass-loader'
      },
      {
        test: /\.(jpe?g|png|gif|svg)$/i,
        loader: 'url-loader',
        options: {
          limit: 10000
        }
      }
    ]
  },
  resolve: {
    extensions: ['.js', '.jsx', '.ts', '.tsx', '.json']
  },
  mode: dev ? 'development' : 'production',
  plugins: dev
    ? [new webpack.HotModuleReplacementPlugin()]
    : [DefinePluginConfig],
  externals: MiskCommon.externals
}
