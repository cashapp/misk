// tslint:disable:no-duplicate-string
const externals = [{
  "@blueprintjs/core": {
    amd: "@blueprintjs/core",
    commonjs: "@blueprintjs/core",
    commonjs2: "@blueprintjs/core",
    root: "window.BlueprintjsCore",
  }
}, {
  "@blueprintjs/icons": {
    amd: "@blueprintjs/icons",
    commonjs: "@blueprintjs/icons",
    commonjs2: "@blueprintjs/icons",
    root: "window.BlueprintjsIcons",
  }
}, {
  "axios": {
    amd: "axios",
    commonjs: "axios",
    commonjs2: "axios",
    root: "window.Axios",
    },
}, {
  "history": {
    amd: "history",
    commonjs: "history",
    commonjs2: "history",
    root: "window.History",
    },
}, {
  "@misk/common": {
    amd: "@misk/common",
    commonjs: "@misk/common",
    commonjs2: "@misk/common",
    root: "window.MiskCommon",
    },
}, {
  "@misk/components": {
    amd: "@misk/components",
    commonjs: "@misk/components",
    commonjs2: "@misk/components",
    root: "window.MiskComponents",
    },
}, {
  "@misk/dev": {
    amd: "@misk/dev",
    commonjs: "@misk/dev",
    commonjs2: "@misk/dev",
    root: "window.MiskDev",
    },
}, {
  "react": {
    amd: "react",
    commonjs: "react",
    commonjs2: "react",
    root: "window.React",
  },
}, {
  "react-dom": {
    amd: "react-dom",
    commonjs: "react-dom",
    commonjs2: "react-dom",
    root: "window.ReactDOM",
  }
}, {
  "react-helmet": {
    amd: "react-helmet",
    commonjs: "react-helmet",
    commonjs2: "react-helmet",
    root: "window.ReactHelmet",
  },
}, {
  "react-hot-loader": {
    amd: "react-hot-loader",
    commonjs: "react-hot-loader",
    commonjs2: "react-hot-loader",
    root: "window.ReactHotLoader",
    },
}, {
  "react-redux": {
    amd: "react-redux",
    commonjs: "react-redux",
    commonjs2: "react-redux",
    root: "window.ReactRedux",
    },
}, {
  "react-router": {
    amd: "react-router",
    commonjs: "react-router",
    commonjs2: "react-router",
    root: "ReactRouter",
    },
}, {
  "react-router-dom": {
    amd: "react-router-dom",
    commonjs: "react-router-dom",
    commonjs2: "react-router-dom",
    root: "window.ReactRouterDom",
    },
}, {
  "redux": {
    amd: "redux",
    commonjs: "redux",
    commonjs2: "redux",
    root: "window.Redux",
    },
}, {
  "styled-components": {
    amd: "styled-components",
    commonjs: "styled-components",
    commonjs2: "styled-components",
    root: "window.StyledComponents",
    },
}];

export default externals;