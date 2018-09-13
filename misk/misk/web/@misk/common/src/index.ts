/**
 * Common Interfaces
 */
import { IconName } from "@blueprintjs/icons"
interface IMiskAdminTab {
  icon: IconName
  name: string
  slug: string
  url_path_prefix: string
}

interface IMiskAdminTabs {
  [key:string]: IMiskAdminTab
}

interface IMiskAdminTabCategories {
  [key:string]: IMiskAdminTab[]
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

export { IMiskAdminTab, IMiskAdminTabs, IMiskAdminTabCategories, IMiskBinder, IMiskBinderKeys, IMiskWindow }