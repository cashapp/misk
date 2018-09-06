const miskTabWebpackBuilder = require("./webpack.config.tab")
const { makeExternals, vendorExternals, miskExternals } = require("./externals")
module.exports = {
  miskTabWebpackBuilder, makeExternals, vendorExternals, miskExternals
}