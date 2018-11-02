import { IWebTab } from "@misk/common"
import * as React from "react"

export interface IMountingDivProps {
  tab: IWebTab
}

export const MountingDivComponent = (props: IMountingDivProps) => (
  <div id={props.tab.slug} />
)
