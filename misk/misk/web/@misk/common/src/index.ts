export * from "./actions"

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
  environment: string
  admin_dashboard_url: string
}

enum IBinderKeys {
  NavTopbarMenu = "NavTopbarMenu",
  TabEntry = "TabEntry",
}

interface IBinder {
  multibind: (binder: IBinderKeys, key: string, value: any) => any
}

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

export { IWebTab, IDashboardTab, IAdminDashboardTab, IServiceMetadata, IBinder, IBinderKeys, IWindow }