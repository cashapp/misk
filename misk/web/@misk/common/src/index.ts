export * from "./actions"

/**
 * Common Interfaces
 */
interface IMiskAdminTab {
  category: string
  name: string
  slug: string
  url_path_prefix: string
}

enum IMiskBinderKeys {
  NavTopbarMenu = "NavTopbarMenu",
  TabEntry = "TabEntry",
}

interface IMiskBinder {
  multibind: (binder: IMiskBinderKeys, key: string, value: any) => any
}

interface IMiskWindow extends Window {
  __REDUX_DEVTOOLS_EXTENSION_COMPOSE__: any
  Misk: {
    Binder: IMiskBinder
    Common: any
    Components: any
    History: any
  }
  MiskTabs: {
    Config: any
    Loader: any
  }
  MiskBinders: any
}

export { IMiskAdminTab, IMiskBinder, IMiskBinderKeys, IMiskWindow }