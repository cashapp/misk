export interface IExternal {
  [key: string]: string|string[]
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

/**
 * 
 * @param inExternals : IExternal
 * 
 * Create Webpack compatible externals object with following modifications
 * - Concatenate scoped package arrays into single strings
 * 
 * Todo
 * - Provide distinct package strings for amd, commonjs, commonjs2, root if necessary
 */
function makeExternals(inExternals: IExternal) : IExternal {
  const outExternals: IExternal = {}
  Object.keys(inExternals).forEach((name, index) => {
    outExternals[name] = inExternals.hasOwnProperty(name) ? 
      (Array.isArray(inExternals[name]) ? 
        (inExternals[name] as string[]).join("") : inExternals[name]
      ) : name
    })
  return outExternals
}
