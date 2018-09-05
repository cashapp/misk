// import path from 'path'
// import webpack from 'webpack'
// const merge = require('webpack-merge')
// const CopyWebpackPlugin = require('copy-webpack-plugin')
// const HTMLWebpackPlugin = require('html-webpack-plugin')

// import MiskCommon from '@misk/common'

export interface IMiskTabWebpack {
  name: string
  slug: string
  relative_path_prefix: string
  port: string
}

// const HotModuleReplacementPlugin = new webpack.HotModuleReplacementPlugin()
// const DefinePlugin = new webpack.DefinePlugin({
//   'process.env.NODE_ENV': JSON.stringify('production')
// })

// const dev = process.env.NODE_ENV !== 'production'

// const staticConfigFields = {
//   output: {
//     publicPath: "/",
//     libraryTarget: 'umd',
//     /**
//      * library will try to bind to browser `window` variable
//      * without below globalObject: library binding to browser `window` 
//      *    fails when run in Node or other non-browser
//      */
//     globalObject: 'typeof self !== \'undefined\' ? self : this'
//   },
//   devServer: {
//     inline: true,
//     hot: true,
//     historyApiFallback: true
//   },
//   module: {
//     rules: [
//       {
//         test: /\.(tsx|ts)$/,
//         exclude: /node_modules/,
//         loaders: 'awesome-typescript-loader'
//       },
//       {
//         enforce: 'pre',
//         test: /\.js$/,
//         loader: 'source-map-loader'
//       },
//       {
//         test: /\.scss$/,
//         loader: 'style-loader!css-loader!sass-loader'
//       },
//       {
//         test: /\.(jpe?g|png|gif|svg)$/i,
//         loader: 'url-loader',
//         options: {
//           limit: 10000
//         }
//       }
//     ]
//   },
//   resolve: {
//     extensions: ['.js', '.jsx', '.ts', '.tsx', '.json']
//   },
//   mode: dev ? 'development' : 'production',
//   plugins: dev
//     ? [HotModuleReplacementPlugin]
//     : [DefinePlugin],
//   externals: MiskCommon.externals
// }

// // const generateDynamicConfigFields = (DIRNAME: string, MiskTabWebpack: IMiskTabWebpack) => {
// //   const RELATIVE_PATH = MiskTabWebpack.relative_path_prefix
// //   return {
// //     entry: ['react-hot-loader/patch', path.join(DIRNAME, '/src/index.tsx')],
// //     output: {
// //       filename: `${RELATIVE_PATH}tab_${MiskTabWebpack.slug}.js`,
// //       path: path.join(DIRNAME, 'dist'),
// //       library: ['MiskTabs', MiskTabWebpack.name],
// //     },
// //     devServer: {
// //       port: MiskTabWebpack.port
// //     },
// //     plugins: 
// //     [new CopyWebpackPlugin([
// //         { from: './node_modules/@misk/common/lib', to: `${RELATIVE_PATH}@misk/`},
// //         { from: './node_modules/@misk/components/lib', to: `${RELATIVE_PATH}@misk/`}
// //       ], 
// //       { debug: 'info', copyUnmodified: true }
// //     ), 
// //     new HTMLWebpackPlugin({
// //         template: path.join(DIRNAME, '/src/index.html'),
// //         filename: 'index.html',
// //         inject: 'body'
// //       })
// //     ],
// //   }
// // }

// const createConfig = (
//   DIRNAME: string, 
//   MiskTabWebpack: IMiskTabWebpack, 
//   otherConfigFields: webpack.Configuration = {}
// ): webpack.Configuration => {
//   // return merge(staticConfigFields, generateDynamicConfigFields(DIRNAME, MiskTabWebpack), otherConfigFields)
//   console.log(DIRNAME, MiskTabWebpack)
//   return merge(staticConfigFields, otherConfigFields)
// }

// export { createConfig }