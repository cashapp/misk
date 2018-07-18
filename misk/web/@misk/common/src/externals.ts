// raw exxternals
export interface IExternal {
  [key: string]: string|string[]
}

interface IInExternal {
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
  "history": "HistoryNPM", 
  "react": "React",
  "react-dom": "ReactDom",
  "react-helmet": "ReactHelmet",
  "react-hot-loader": "ReactHotLoader",
  "react-redux": "ReactRedux",
  "react-router": "ReactRouter",
  "react-router-dom": "ReactRouterDom",
  "react-router-redux": "ReactRouterRedux",
  "redux": "Redux",
  "styled-components": "StyledComponents"
})

function makeExternals(inExternals: IInExternal) : IExternal {
  const outExternals: IExternal = {}
  Object.keys(inExternals).forEach((name, index) => {
    outExternals[name] = inExternals.hasOwnProperty(name) ? (Array.isArray(inExternals[name]) ? (inExternals[name] as string[]).join("") : inExternals[name]) : name
  })
  return outExternals
}

// // html-webpack-externals-plugin
// export interface IExternal {
//   module: string,
//   entry: string,
//   global: string|string[],
// }

// interface IInExternal {
//   [key: string]: string|string[]
// }

// export default makeExternals({
//   "@blueprintjs/core": ["Blueprint", "Core"],
//   "@blueprintjs/icons": ["Blueprint", "Icons"],
//   "@misk/common": ["Misk", "Common"],
//   "@misk/components": ["Misk", "Components"],
//   "@misk/dev": ["Misk", "Dev"],
//   "axios": "Axios",
//   "connected-react-router": "ConnectedReactRouter",
//   "history": "HistoryNPM", 
//   "react": "React",
//   "react-dom": "ReactDom",
//   "react-helmet": "ReactHelmet",
//   "react-hot-loader": "ReactHotLoader",
//   "react-redux": "ReactRedux",
//   "react-router": "ReactRouter",
//   "react-router-dom": "ReactRouterDom",
//   "react-router-redux": "ReactRouterRedux",
//   "redux": "Redux",
//   "styled-components": "StyledComponents"
// })

// function makeExternals(inExternals: IInExternal) : IExternal[] {
//   const outExternals: IExternal[] = []
//   Object.keys(inExternals).forEach((name, index) => {
//     outExternals.push({
//       entry: name.startsWith("@") ? "../../@misk/common/lib/static/vendors.js" : "../@misk/common/lib/static/vendors.js",
//       global: inExternals.hasOwnProperty(name) ? (Array.isArray(inExternals[name]) ? (inExternals[name] as string[]).join("") : inExternals[name]) : name,
//       module: name
//     }) 
//   })
//   return outExternals
// }