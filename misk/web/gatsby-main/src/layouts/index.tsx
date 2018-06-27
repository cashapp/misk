import { Alignment, Button, Menu, MenuItem, Navbar, NavbarDivider, NavbarGroup, NavbarHeading } from "@blueprintjs/core"
import { Link } from "gatsby"
import React from "react"
import Container from "../components/container"
import { rhythm } from "../utils/typography"

import menu, { menuItem } from "../utils/menu"
import routes from "../utils/routes"

export interface ListLinkProps {
  to: string,
  children: any
}

class ListLink extends React.Component<ListLinkProps> {
  constructor(props: ListLinkProps) {
    super(props)
  }

  render() {
    return(
      <li style={{ display: `inline-block`, marginRight: `1rem` }}>
        <Link to={this.props.to}>
          {this.props.children}
        </Link>
      </li>
    )
  }
}

const MainLayout = ({ children }: { children: any }) => (
  // <div
  //   style={{
  //     margin: `0 auto`,
  //     marginBottom: rhythm(1.5),
  //     marginTop: rhythm(1.5),
  //     maxWidth: 650,
  //     paddingLeft: rhythm(3 / 4),
  //     paddingRight: rhythm(3 / 4),
  //   }}
  // >
  <div>
    <link href="https://unpkg.com/normalize.css@^7.0.0" rel="stylesheet" />
    <link href="https://unpkg.com/@blueprintjs/core@^2.0.0/lib/css/blueprint.css" rel="stylesheet" />
    <link href="https://unpkg.com/@blueprintjs/icons@^2.0.0/lib/css/blueprint-icons.css" rel="stylesheet" />
    <link href="https://unpkg.com/skeleton-plus/dist/skeleton-plus.min.css" rel="stylesheet" />

    <Navbar>
      <NavbarGroup align={Alignment.LEFT}>
        <NavbarHeading>Misk Admin</NavbarHeading>
        <NavbarDivider/>
      </NavbarGroup>
    </Navbar>
    <div style={{position: `absolute`,}}>
      <Menu>
        {menu.map(({ text, icon="document", className="pt-minimal", url } : any) => (
          <Link key={url} to={url}><MenuItem key={url} href={url} className={className} icon={icon} text={text}/></Link>
        ))}
        {/* <Region name="mainMenu"/> */}
      </Menu>
    </div>
    <div className="container misk-main-container">
      <div className="row">
        <div className="twelve columns">
          {/* <Switch>
            {routes.map(({ path, exact, component: C }) => (
              <Route key={path} path={path} exact={exact} component={C}/>
            ))}
          </Switch> */}
        </div>
      </div>
    </div>
    <Container>
      {children}
    </Container>
  </div>
)

export default MainLayout
