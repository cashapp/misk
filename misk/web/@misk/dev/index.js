const miskTabBuilder = require("./webpack.config.tab")
const { makeExternals, vendorExternals, miskExternals } = require("./externals")
module.exports = {
  miskTabBuilder, makeExternals, vendorExternals, miskExternals
}