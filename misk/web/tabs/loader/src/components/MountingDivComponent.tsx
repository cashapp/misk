import { IMiskAdminTab } from "@misk/common"
import * as React from "react"

export interface IMountingDivProps {
  tab: IMiskAdminTab,
}

export const MountingDivComponent = (props: IMountingDivProps) => (
  <div id={props.tab.slug}>
    <span>{props.tab.name} is loading...</span>
  </div>
)