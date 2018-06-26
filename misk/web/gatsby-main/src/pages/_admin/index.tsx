import { Alignment, Button, Menu, MenuItem, Navbar, NavbarDivider, NavbarGroup, NavbarHeading } from "@blueprintjs/core"
import { Link } from "gatsby"
import * as React from "react"
import Layout from "../../layouts"
import menu, { menuItem } from "../../utils/menu"
import routes from "../../utils/routes"
// Please note that you can use https://github.com/dotansimha/graphql-code-generator
// to generate all types from graphQL schema
interface IndexPageProps {
  data: {
    site: {
      siteMetadata: {
        siteName: string
      }
    }
  }
}

export const pageQuery = graphql`
  query IndexQuery {
    site {
      siteMetadata {
        siteName
      }
    }
  }
`

// activeOnlyWhenExact: boolean
//   className: string,
//   icon: BlueprintjsIconName,
//   text: string,
//   url: string,

export default ({ data }: IndexPageProps) => {
  const { siteName } = data.site.siteMetadata
  return (
    <Layout>
      <h1>Home</h1>
    </Layout>
  )
}
