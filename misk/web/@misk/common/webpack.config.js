const path = require('path');

module.exports = {
  mode: 'production',
  entry: {
    index: path.resolve(__dirname, 'src/index.ts'),
    externals: path.resolve(__dirname, 'src/index.ts'),
    styles: path.resolve(__dirname, 'src/styles.ts'),
    vendors: path.resolve(__dirname, 'src/vendors.ts')
  },
  devtool: 'source-map',
  output: {
    path: path.resolve(__dirname, './lib'),
    filename: '[name].js',
    library: ['misk', 'common'],
    libraryTarget: 'umd',
    globalObject: 'typeof window !== \'undefined\' ? window : this'
  },
  module: {
    rules: [
      { 
        test: /\.tsx?$/, 
        loader: 'awesome-typescript-loader'
      }, {
        test: /\.(scss|sass|css)$/,
        loader: ['css-loader?minimize=true', 'sass-loader']
      }
    ]
  },
  resolve: {
    extensions: [".ts", ".tsx", ".js", ".jsx", ".json"]
  },
};