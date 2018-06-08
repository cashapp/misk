const path = require('path');

module.exports = {
  entry: {
    miskConfig: path.resolve(__dirname, 'src/index.ts'),
    styles: path.resolve(__dirname, 'src/styles.ts'),
    vendor: path.resolve(__dirname, 'src/vendor.ts')
  },
  devtool: 'source-map',
  output: {
    path: path.resolve(__dirname, './lib'),
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
      },
    ]
  },
  resolve: {
    extensions: [".ts", ".tsx", ".js", ".jsx", ".json"]
  },
};