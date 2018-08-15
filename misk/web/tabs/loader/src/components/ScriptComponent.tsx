import { Icon } from "@blueprintjs/core"
import { IMiskAdminTab } from "@misk/common"
import * as React from "react"
import Helmet from "react-helmet"

interface IScriptProps {
  tab: IMiskAdminTab,
}

export const ScriptComponent = (props: IScriptProps) => {
  return (
    <div>
      <div id={props.tab.slug}>
        <p>{props.tab.name}: {props.tab.slug} {props.tab.url_path_prefix} <Icon icon={props.tab.icon}/></p>
      </div>
      <Helmet>
        {/* <script async src={`${props.tab.url_path_prefix}/tab_${props.tab.slug}.js`}/> */}
      </Helmet>     
    </div>
  )
}