const path = require('path');
const HtmlWebPackPlugin = require("html-webpack-plugin");
const TsconfigPathsPlugin = require('tsconfig-paths-webpack-plugin');

module.exports = {
  mode: 'development',
  entry: path.resolve(__dirname, 'src/index.tsx'),
  module: {
    rules: [
      {
        test: /\.(ts|tsx)$/,
        use: "babel-loader",
        exclude: /node_modules/
      },{
        test: /\.html$/,
        use: 'html-loader'
      }
    ],
  },
  resolve: {
    extensions: ['.tsx', '.ts', '.js',],
    alias: {
      '@web-actions': path.resolve(__dirname, 'src/web-actions/')
    },
    plugins: [new TsconfigPathsPlugin({ configFile: "./tsconfig.json" })],
  },
  performance: {
    maxAssetSize: 2000000,
    maxEntrypointSize: 2000000,
  },
  plugins: [
    new HtmlWebPackPlugin({
      template: path.resolve(__dirname, 'src/index.html'),
      filename: "./index.html"
    })
  ],
  output: {
    filename: 'bundle.js',
    path: path.resolve(__dirname, 'lib'),
  },
  devServer: {
    static: {
      directory: path.join(__dirname, 'lib'),
    },
    port: 9000,
    open: true,
    hot: true,
    proxy: [
      {
        context: ['/api', '/squareup'],
        target: 'http://127.0.0.1:8080',
      },
    ],
  },
};
