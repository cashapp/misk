// tslint:disable-next-line:no-implicit-dependencies
import { IconName as BlueprintjsIconName } from "@blueprintjs/icons"

// tslint:disable-next-line:class-name
export interface menuItem {
  activeOnlyWhenExact: boolean
  className: string,
  icon: BlueprintjsIconName,
  text: string,
  url: string,
}

const menu =  [{
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

export default menu