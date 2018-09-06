/**
 * Create Webpack compatible externals object with compatible entries for amd, commonjs, commonjs2, root
 */
const makeExternals = (inExternals) => {
  const outExternals = {}
  Object.keys(inExternals).map((pkg) => {
    outExternals[pkg] = {
      amd: pkg,
      commonjs: pkg,
      commonjs2: pkg,
      root: inExternals[pkg],
    }
  })
  return outExternals
}

const vendorExternals = makeExternals({
  "@blueprintjs/core": ["Blueprint", "Core"],
  "@blueprintjs/icons": ["Blueprint", "Icons"],
  "axios": "Axios",
  "connected-react-router": "ConnectedReactRouter",
  "dayjs": "Dayjs",
  "history": "HistoryNPM",
  "immutable": "Immutable",
  "react": "React",
  "react-dom": "ReactDom",
  "react-helmet": "ReactHelmet",
  "react-hot-loader": "ReactHotLoader",
  "react-redux": "ReactRedux",
  "react-router": "ReactRouter",
  "react-router-dom": "ReactRouterDom",
  "react-router-redux": "ReactRouterRedux",
  "redux": "Redux",
  "redux-saga": "ReduxSaga",
  "styled-components": "StyledComponents"
})

const miskExternals = makeExternals({
  "@misk/components": ["Misk", "Components"]
})

module.exports = { makeExternals, vendorExternals, miskExternals }