const { miskTabWebpackBuilder } = require("@misk/dev")
const path = require('path')
const miskTabWebpack = require(path.join(process.cwd(), "package.json")).miskTabWebpack
module.exports = miskTabWebpackBuilder(process.env.NODE_ENV, {
  "dirname": __dirname,
  miskTabWebpack
})