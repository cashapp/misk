const { MiskWebpackConfigBase } = require("@misk/dev")
const path = require('path')
const miskTabWebpack = require(path.join(process.cwd(), "package.json")).miskTabWebpack
module.exports = MiskWebpackConfigBase(process.env.NODE_ENV, {
  "dirname": __dirname,
  miskTabWebpack,
})
