const { createMiskWebpackConfig } = require("@misk/webpack")
const path = require("path")

const MiskTabConfig = require(path.join(process.cwd(), "package.json")).miskTabWebpack
module.exports = createMiskWebpackConfig(__dirname, MiskTabConfig)