import * as React from "react"
import { Helmet } from "react-helmet"

interface ITabProps {
  slug: string,
  url: string
}

export class TabContainer extends React.Component<ITabProps, {}> {
  constructor(props: ITabProps) {
    super(props)
  }

  render() {
    return (
      <div>
        <Helmet>
          <script src="{this.props.url}" type="text/javascript" />
        </Helmet>
        <div className="container misk-main-container">
          <div className="row">
              <div className="twelve columns">
              <div id={this.props.slug}/>
              </div>
          </div>
        </div>
      </div>
    )
  }
}
