import { Icon } from "@blueprintjs/core"
import * as React from "react"
import Helmet from "react-helmet"
import styled from "styled-components" 
import { IAdminTab } from "../containers/LoaderContainer"

interface IScriptProps {
  tab: IAdminTab,
}

const TabScript = styled.div``

export const ScriptComponent = (props: IScriptProps) => {
  return (
    <TabScript>
      <div id={props.tab.slug}>
        <p>{props.tab.name}: {props.tab.slug} {props.tab.url_path_prefix} <Icon icon={props.tab.icon}/></p>
      </div>
      <Helmet>
        {/* <script async src={`${props.tab.url_path_prefix}/tab_${props.tab.slug}.js`}/> */}
      </Helmet>     
    </TabScript>
  )
}