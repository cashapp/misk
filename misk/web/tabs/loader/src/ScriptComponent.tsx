import * as React from "react"
import Helmet from "react-helmet"
import styled from "styled-components" 
import { IAdminTab } from "./Loader"

interface IScriptProps {
  tab: IAdminTab,
}

const TabScript = styled.div``

export const ScriptComponent = (props: IScriptProps) => {
  return (
    <TabScript>
      <p>{props.tab.name}</p>
      <Helmet>
        <script async src={`${props.tab.url_path_prefix}/tab_${props.tab.slug}.js`}/>
      </Helmet>
    </TabScript>
  )
}