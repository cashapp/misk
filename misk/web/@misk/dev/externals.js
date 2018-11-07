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
  "@blueprintjs/datetime": ["Blueprint", "Datetime"],
  "@blueprintjs/icons": ["Blueprint", "Icons"],
  "@blueprintjs/select": ["Blueprint", "Select"],
  "@blueprintjs/table": ["Blueprint", "Table"],
  "@blueprintjs/timezone": ["Blueprint", "Timezone"],
  axios: "Axios",
  "connected-react-router": "ConnectedReactRouter",
  dayjs: "Dayjs",
  history: "HistoryNPM",
  immutable: "Immutable",
  "lodash-es": "_",
  react: "React",
  "react-dom": "ReactDom",
  "react-helmet": "ReactHelmet",
  "react-hot-loader": "ReactHotLoader",
  "react-redux": "ReactRedux",
  "react-router": "ReactRouter",
  "react-router-dom": "ReactRouterDom",
  redux: "Redux",
  "redux-saga": "ReduxSaga",
  "redux-saga/effects": "ReduxSagaEffects",
  "styled-components": "StyledComponents"
})

const miskExternals = createExternals({
  "@misk/common": ["Misk", "Common"],
  "@misk/components": ["Misk", "Components"]
})

module.exports = { createExternals, vendorExternals, miskExternals }
