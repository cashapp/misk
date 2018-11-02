/**
 * @misk/common/refresh
 *
 * Certain files are cached in @misk/common in order to provide
 *  singular styles and vendors includes for each Misk tabs index.html
 * This file updates these cached files with the latest version on
 *  each new publish of @misk/common
 * Add a new cached file by updating the "miskCachedUrls" in `package.json`
 */

"use strict"

const fs = require("fs")
const path = require("path")
const axios = require("axios")

const downloadUrlToFile = (url, filepath) => {
  axios({
    method: "GET",
    url: url,
    responseType: "stream"
  })
    .then(response => {
      response.data.pipe(fs.createWriteStream(filepath))
    })
    .catch(error => {
      console.log(error)
    })
}

fs.mkdir("cachedUrls", error => {
  if (error && !error.code === "EEXIST") console.log(error)
})
const miskCachedUrls = require(path.join(process.cwd(), "package.json"))
  .miskCachedUrls
Object.entries(miskCachedUrls).map(([taskname, { filepath, url }]) => {
  console.log("RefreshCachedUrl:", taskname)
  downloadUrlToFile(url, path.resolve(__dirname, "cachedUrls", filepath))
})
