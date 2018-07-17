export interface IExternal {
  [key: string]: {
    amd: string,
    commonjs: string,
    commonjs2: string,
    root: string|string[]
  }
}

interface IInExternal {
  [key: string]: string|string[]
}

export default makeExternals({
  "@blueprintjs/core": ["Blueprint", "Core"],
  "@blueprintjs/icons": ["Blueprint", "Icons"],
  "axios": "Axios",
  "history": "HistoryNPM", 
  "@misk/common": ["Misk", "Common"],
  "@misk/components": ["Misk", "Components"],
  "@misk/dev": ["Misk", "Dev"],
  "react": "React", // has to be kept locally because of react-hot-loader bug
  "react-dom": "ReactDom",
  "react-helmet": "ReactHelmet",
  "react-hot-loader": "ReactHotLoader",
  "react-redux": "ReactRedux", // has to be kept locally because of connected-react-router bug
  "react-router": "ReactRouter",
  "react-router-dom": "ReactRouterDom",
  "redux": "Redux", // has to be kept locally because of index CombineReducers bug
  "styled-components": "StyledComponents" // has to be kept locally because of PathDebugComponent bug
})

function makeExternals(inExternals: IInExternal) : IExternal {
  const outExternals: IExternal = {}
  Object.keys(inExternals).forEach((name, index) => {
    outExternals[name] = {
      amd: name,
      commonjs: name,
      commonjs2: name,
      root: inExternals.hasOwnProperty(name) ? inExternals[name] : name
    }
  })
  return outExternals
}