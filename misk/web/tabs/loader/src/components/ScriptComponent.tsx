import { IMiskAdminTab } from "@misk/common"
import * as React from "react"
import Helmet from "react-helmet"
import { MountingDivComponent } from "../components"

export interface IScriptProps {
  tab: IMiskAdminTab,
}

export const ScriptComponent = (props: IScriptProps) => {
  return (
    <div>
      <Helmet>
        <title>Test Title</title>
        <script async={true} src={`${props.tab.url_path_prefix}/tab_${props.tab.slug}.js`}/>
      </Helmet>
      <MountingDivComponent tab={props.tab}/>
    </div>
  )
}