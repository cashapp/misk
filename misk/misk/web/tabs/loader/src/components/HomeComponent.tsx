import { IMiskAdminTabs } from "@misk/common"
import * as React from "react"
import { Link } from "react-router-dom"

export interface IHomeProps {
  tabs: IMiskAdminTabs,
}

const tabLinks = (adminTabs: IMiskAdminTabs) => Object.entries(adminTabs).map(([,tab]) => <Link key={tab.slug} to={`/_admin/${tab.slug}/`}>{tab.name}<br/></Link>)

export const HomeComponent = (props: IHomeProps) => (
  <div>
    <h1>Loader Debug</h1>
    <Link to="/_admin/">Home</Link><br/>
    {props.tabs ? tabLinks(props.tabs) : <span>Loading...<br/></span>}
    <Link to="/_admin/asdf/asdf/asdf/asdf/">Bad Link</Link><br/>
  </div>
)