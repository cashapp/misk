import { IconName as BlueprintjsIconName } from '@blueprintjs/icons'

export type menuItem = {
  text: string,
  icon: BlueprintjsIconName,
  className: string,
  url: string,
  activeOnlyWhenExact: boolean
}

const menu =  [{
  text: "Home",
  icon: "home",
  url: "/_admin/",
}, {
  text: "Config",
  icon: "properties",
  url: "/_admin/config",
}, {
  text: "Healthcheck",
  icon: "heart-broken",
  url: "/_admin/healthcheck",
}]

export default menu