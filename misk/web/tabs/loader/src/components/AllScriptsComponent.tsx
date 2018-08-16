import { IMiskAdminTab, IMiskAdminTabs } from "@misk/common"
import * as React from "react"
import { ScriptComponent } from "./ScriptComponent"

export interface IAllScriptsProps {
  tabs: IMiskAdminTabs,
}

export const AllScriptsComponent = (props: IAllScriptsProps) => {
  Object.entries(props.tabs).map(([key,tab]) => (<ScriptComponent tab={tab}/>))
}