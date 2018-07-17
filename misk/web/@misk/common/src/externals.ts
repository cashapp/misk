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
  "axios": "axios",
  "history": "history", 
  "@misk/common": ["Misk", "Common"],
  "@misk/components": ["Misk", "Components"],
  "@misk/dev": ["Misk", "Dev"],
  "react": "React",
  "react-dom": "ReactDom",
  "react-helmet": "ReactHelmet",
  "react-hot-loader": "ReactHotLoader",
  "react-redux": "ReactRedux",
  "react-router-dom": "ReactRouterDom",
  "redux": "Redux",
  "styled-components": "StyledComponents"
})

function makeExternals(inExternals: IInExternal) : IExternal {
  const outExternals: IExternal = {}
  Object.keys(inExternals).forEach((name, external) => {
    outExternals[name] = {
      amd: name,
      commonjs: name,
      commonjs2: name,
      root: inExternals.hasOwnProperty(name) ? inExternals[name] : name
    }
  })



  // for (const packageName in inExternals) {
  //   outExternals[packageName] = {
  //     amd: packageName,
  //     commonjs: packageName,
  //     commonjs2: packageName,
  //     root: inExternals.hasOwnProperty(packageName) ? inExternals[packageName] : packageName
  //   }
  // }
  return outExternals
}