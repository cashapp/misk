const path = require('path');

module.exports = {
  name: "library",
  mode: 'production',
  entry: {
    common: path.resolve(__dirname, 'src/index.ts'),
    externals: path.resolve(__dirname, 'src/externals.ts'),
    interfaces: path.resolve(__dirname, 'src/interfaces.ts')
  },
  devtool: 'source-map',
  output: {
    path: path.resolve(__dirname, './lib'),
    filename: '[name].js',
    library: ['Misk', 'Common'],
    libraryTarget: 'umd',
    /**
     * library will try to bind to browser `window` variable
     * without below globalObject: library binding to browser `window` 
     *    fails when run in Node or other non-browser
     */
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