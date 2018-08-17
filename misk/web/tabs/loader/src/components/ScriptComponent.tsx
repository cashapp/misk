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
        <script async={true} src={`http://0.0.0.0:8080/_tab/${props.tab.slug}/tab_${props.tab.slug}.js`}/>
      </Helmet>
      <MountingDivComponent tab={props.tab}/>
    </div>
  )
}