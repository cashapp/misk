///<reference types="react" />
import { fromJS, List } from "immutable"
export * from "./actions"
export * from "./css"

/**
 * Common Interfaces
 */
interface IWebTab {
  slug: string
  url_path_prefix: string
  roles?: string[]
  services?: string[]
}

interface IDashboardTab extends IWebTab {
  name: string
  category?: string
}

interface IAdminDashboardTab extends IDashboardTab {}

interface IServiceMetadata {
  app_name: string
  environment: Environment
  admin_dashboard_url: string
  navbar_items: Array<string | Element | JSX.Element>
  navbar_status: string | Element | JSX.Element
}

/**
 * Environment
 */
enum Environment {
  TESTING = "TESTING",
  DEVELOPMENT = "DEVELOPMENT",
  STAGING = "STAGING",
  PRODUCTION = "PRODUCTION"
}

/**
 * Ducks
 */
interface IDefaultState {
  data?: any
  error?: any
  loading?: boolean
  success?: boolean
  toJS?: () => any
}

const defaultState: IDefaultState = fromJS({
  data: List([]),
  error: null,
  loading: false,
  success: false
})

/**
 * Binder
 */
enum IBinderKeys {
  NavNavbarMenu = "NavNavbarMenu",
  TabEntry = "TabEntry"
}

interface IBinder {
  multibind: (binder: IBinderKeys, key: string, value: any) => any
}

/**
 * Window
 */
interface IWindow extends Window {
  __REDUX_DEVTOOLS_EXTENSION_COMPOSE__: any
  Misk: {
    Binder: IBinder
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

export {
  IWebTab,
  IDashboardTab,
  IAdminDashboardTab,
  IServiceMetadata,
  defaultState,
  Environment,
  IDefaultState,
  IBinder,
  IBinderKeys,
  IWindow
}
