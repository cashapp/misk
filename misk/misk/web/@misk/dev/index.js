const createTabWebpack = require("./webpack.config.tab")
const createPrettierConfig = require("./prettier.config.base")
const vscodeExtensions = require("./vscode.extensions")
const vscodeSettings = require("./vscode.settings")
const {
  createExternals,
  vendorExternals,
  miskExternals
} = require("./externals")
module.exports = {
  createPrettierConfig,
  createTabWebpack,
  vscodeExtensions,
  vscodeSettings,
  createExternals,
  vendorExternals,
  miskExternals
}
