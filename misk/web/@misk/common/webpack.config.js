const path = require('path');

module.exports = {
  name: "library",
  mode: 'production',
  entry: {
    index: path.resolve(__dirname, 'src/index.ts'),
    externals: path.resolve(__dirname, 'src/index.ts')
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
        test: /\.(ts|tsx)?$/, 
        loader: 'awesome-typescript-loader'
      }
    ]
  },
  resolve: {
    extensions: [".ts", ".tsx"]
  },
};