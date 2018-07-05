import { IconName as BlueprintjsIconName } from "@blueprintjs/icons"

export interface IMenuItem {
  activeOnlyWhenExact?: boolean
  className?: string,
  icon: BlueprintjsIconName,
  text: string,
  url: string,
}

const menuItems: IMenuItem[] =  [{
  icon: "home",
  text: "Home",
  url: "/_admin/",
}, {
  icon: "properties",
  text: "Config",
  url: "/_admin/config",
}, {
  icon: "heart-broken",
  text: "Healthcheck",
  url: "/_admin/healthcheck",
}]

export default menuItems