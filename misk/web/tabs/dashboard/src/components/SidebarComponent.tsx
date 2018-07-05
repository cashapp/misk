import { Menu, MenuItem } from "@blueprintjs/core"
import * as React from "react"
import { Helmet } from "react-helmet"
import { Link } from "react-router-dom"
import { IMenuItem } from "../utils/menu";

interface ISidebarProps {
  menuItems: IMenuItem[]
}

export class SidebarComponent extends React.Component<ISidebarProps, {}> {
  constructor(props: ISidebarProps) {
    super(props)
  }

  render() {
    return (
      <div style={{position: `absolute`,}}>
        <Menu>
          {this.props.menuItems.map(({ text, icon="document", className="pt-minimal", url } : any) => (<Link key={url} to={url}><MenuItem key={url} href={url} className={className} icon={icon} text={text}/></Link>))}
        </Menu>
      </div>
    )
  }
}
