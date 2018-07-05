import { Menu, MenuItem } from "@blueprintjs/core"
import * as React from "react"
import { Helmet } from "react-helmet"
import { Link } from "react-router-dom"
import { IMenuItem } from "../utils/menu";

interface ISidebarProps {
  menuItems: IMenuItem[]
}



export class SidebarComponent extends React.Component<ISidebarProps, {}> {
  // const renderedMenuItems = {
  //   this.props.menuItems.map(
  //     ({ text, icon="document", className="pt-minimal", url } : any) => (
  //       <Link key={url} to={url}>
  //         <MenuItem key={url} to={url} className={className} icon={icon} text={text}/>
  //       </Link>)
  //     )
  // }
  private renderedMenuItems: any

  constructor(props: ISidebarProps) {
    super(props)
    this.renderedMenuItems = this.props.menuItems.map(
      ({ text, icon="document", className="pt-minimal", url } : any) => (
          <MenuItem key={url} href={url} className={className} icon={icon} text={text}/>
        )
      )
  }

  render() {
    return (
      <div style={{position: `absolute`,}}>
        <Menu>
          {this.renderedMenuItems}
        </Menu>
      </div>
    )
  }
}
