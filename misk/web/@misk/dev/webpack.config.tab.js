const { vendorExternals, miskExternals } = require('./externals')
const CopyWebpackPlugin = require('copy-webpack-plugin')
const HTMLWebpackPlugin = require('html-webpack-plugin')
const path = require('path')
const webpack = require('webpack')
const merge = require('webpack-merge')

module.exports = (env, argv, otherConfigFields = {}) => {
  const { dirname, miskTabWebpack } = argv
  
  const DefinePluginConfig = new webpack.DefinePlugin({
    'process.env.NODE_ENV': JSON.stringify('production')
  })
  
  const HTMLWebpackPluginConfig = new HTMLWebpackPlugin({
    template: path.join(dirname, '/src/index.html'),
    filename: 'index.html',
    inject: 'body'
  })
  
  const CopyWebpackPluginConfig = new CopyWebpackPlugin(
    [
      { from: './node_modules/@misk/common/lib', to: `${miskTabWebpack.relative_path_prefix}@misk/`},
      { from: './node_modules/@misk/components/lib', to: `${miskTabWebpack.relative_path_prefix}@misk/`}
    ], 
    { copyUnmodified: true }
  )

  const baseConfigFields = {
    entry: ['react-hot-loader/patch', path.join(dirname, '/src/index.tsx')],
    output: {
      filename: `${miskTabWebpack.relative_path_prefix}tab_${miskTabWebpack.slug}.js`,
      path: path.join(dirname, miskTabWebpack.output_path),
      publicPath: "/",
      library: ['MiskTabs', miskTabWebpack.name],
      libraryTarget: 'umd',
      /**
       * library will try to bind to browser `window` variable
       * without below globalObject: library binding to browser `window` 
       *    fails when run in Node or other non-browser
       */
      globalObject: 'typeof self !== \'undefined\' ? self : this'
    },
    devServer: {
      port: miskTabWebpack.port,
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
    mode: env !== 'production' ? 'development' : 'production',
    plugins: [CopyWebpackPluginConfig, HTMLWebpackPluginConfig]
      .concat(env !== 'production'
      ? [new webpack.HotModuleReplacementPlugin()]
      : [DefinePluginConfig]),
    externals: { ...vendorExternals, ...miskExternals }
  }
  
  return merge(baseConfigFields, otherConfigFields)
}
