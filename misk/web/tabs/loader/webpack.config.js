const { miskTabBuilder } = require("@misk/dev")
const path = require('path')
const miskTab = require(path.join(process.cwd(), "package.json")).miskTab
module.exports = miskTabBuilder(process.env.NODE_ENV, {
  "dirname": __dirname,
  miskTab
})