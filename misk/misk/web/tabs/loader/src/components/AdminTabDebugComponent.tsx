import { Icon } from "@blueprintjs/core"
import { IMiskAdminTab } from "@misk/common"
import * as React from "react"

export interface IAdminTabDebugProps {
  tab: IMiskAdminTab,
}

export const AdminTabDebugComponent = (props: IAdminTabDebugProps) => (
  <div>
    <p>{props.tab.name}: {props.tab.slug} {props.tab.url_path_prefix} <Icon icon={props.tab.icon}/></p>
  </div>
)