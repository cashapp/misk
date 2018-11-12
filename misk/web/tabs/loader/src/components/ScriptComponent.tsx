import { IWebTab } from "@misk/common"
import * as React from "react"
import Helmet from "react-helmet"
import { MountingDivComponent } from "../components"

export interface IScriptProps {
  tab: IWebTab
}

export const ScriptComponent = (props: IScriptProps) => {
  return (
    <span>
      <Helmet>
        <script
          async={true}
          src={`/_tab/${props.tab.slug}/tab_${props.tab.slug}.js`}
        />
      </Helmet>
      <MountingDivComponent tab={props.tab} />
    </span>
  )
}
