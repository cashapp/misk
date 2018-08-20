export interface IInExternal {
  [key: string]: string|string[]
}

export interface IOutExternal {
  [key: string]: {
    amd: string
    commonjs: string
    commonjs2: string
    root: string|string[]
  }
}

/**
 * 
 * @param inExternals : IExternal
 * 
 * Create Webpack compatible externals object with compatible entries for amd, commonjs, commonjs2, root
 */
export const makeExternals = (inExternals: IInExternal) : IOutExternal => {
  const outExternals: IOutExternal = {}
  Object.keys(inExternals).map((pkg, index) => {
    outExternals[pkg] = {
      commonjs: pkg,
      commonjs2: pkg,
      amd: pkg,
      root: inExternals[pkg],
    }
  })
  return outExternals
}

export const externals = makeExternals({
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