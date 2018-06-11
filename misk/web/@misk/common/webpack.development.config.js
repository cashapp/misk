const path = require('path');

module.exports = {
  mode: 'development',
  entry: {
    styles: path.resolve(__dirname, 'src/styles.ts'),
    vendors: path.resolve(__dirname, 'src/vendors.ts')
  },
  devtool: 'cheap-module-eval-source-map',
  output: {
    path: path.resolve(__dirname, '../../main/build/js'),
    filename: '[name].js'
  },
  module: {
    rules: [
      { 
        test: /\.tsx?$/, 
        loader: "awesome-typescript-loader" 
      },
      {
        test: /\.(scss|sass|css)$/,
        loader: ['style-loader', 'css-loader', 'sass-loader']
      }
    ]
  },
  resolve: {
    extensions: [".ts", ".tsx", ".js", ".jsx", ".json"]
  },
};