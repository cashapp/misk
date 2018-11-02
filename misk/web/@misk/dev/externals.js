/**
 * Create Webpack compatible externals object with compatible entries for amd, commonjs, commonjs2, root
 */
const createExternals = inExternals => {
  const outExternals = {}
  Object.keys(inExternals).map(pkg => {
    outExternals[pkg] = {
      amd: pkg,
      commonjs: pkg,
      commonjs2: pkg,
      root: inExternals[pkg]
    }
  })
  return outExternals
}

const vendorExternals = createExternals({
  "@blueprintjs/core": ["Blueprint", "Core"],
  "@blueprintjs/icons": ["Blueprint", "Icons"],
  axios: "Axios",
  "connected-react-router": "ConnectedReactRouter",
  dayjs: "Dayjs",
  history: "HistoryNPM",
  immutable: "Immutable",
  "lodash-es": "_",
  react: "React",
  "react-dom": "ReactDom",
  "reaat-emotion": "ReactEmotion",
  "react-helmet": "ReactHelmet",
  "react-hot-loader": "ReactHotLoader",
  "react-redux": "ReactRedux",
  "react-router": "ReactRouter",
  "react-router-dom": "ReactRouterDom",
  redux: "Redux",
  "redux-saga": "ReduxSaga",
  "redux-saga/effects": "ReduxSagaEffects"
})

const miskExternals = createExternals({
  "@misk/common": ["Misk", "Common"],
  "@misk/components": ["Misk", "Components"]
})

module.exports = { createExternals, vendorExternals, miskExternals }
