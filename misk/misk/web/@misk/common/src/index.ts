export * from "./externals"

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

interface IMiskWindow extends Window {
  __REDUX_DEVTOOLS_EXTENSION_COMPOSE__: any
  Misk: {
    Binder: any
    Common: any
    Components: any
    History: any
  }
  MiskTabs: {
    Config: any
    Loader: any
  }
}

interface IMiskWebpackContext {
  miskLibraryPath: string
  outputFileName: string
  libraryName: string
  devServerPort: number
}

export { IMiskAdminTab, IMiskAdminTabs, IMiskWebpackContext, IMiskWindow }