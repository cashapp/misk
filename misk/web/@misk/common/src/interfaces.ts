import { IconName } from "@blueprintjs/icons"

export interface IMiskAdminTab {
  icon: IconName
  name: string
  slug: string
  url_path_prefix: string
}

export interface IMiskAdminTabs {
  [key:string]: IMiskAdminTab
}
