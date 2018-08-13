export interface IExternal {
  [key: string]: string|string[]
}

export default makeExternals({
  "@blueprintjs/core": ["Blueprint", "Core"],
  "@blueprintjs/icons": ["Blueprint", "Icons"],
  "@misk/common": ["Misk", "Common"],
  "@misk/components": ["Misk", "Components"],
  "@misk/dev": ["Misk", "Dev"],
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

function makeExternals(inExternals: IExternal) : IExternal {
  const outExternals: IExternal = {}
  Object.keys(inExternals).forEach((name, index) => {
    outExternals[name] = inExternals.hasOwnProperty(name) ? (Array.isArray(inExternals[name]) ? (inExternals[name] as string[]).join("") : inExternals[name]) : name
  })
  return outExternals
}
