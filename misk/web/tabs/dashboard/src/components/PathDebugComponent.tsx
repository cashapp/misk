import * as React from "react"
import styled from "styled-components" 

interface IPathProps {
  hash: string,
  pathname: string,
  search: string
}

const PathDebug = styled.div`
`

export class PathDebugComponent extends React.Component<IPathProps> {
  constructor(props: IPathProps) {
    super(props)
  }

  render() {
    return (
      <PathDebug>
        <p>hash: {this.props.hash}</p>
        <p>pathname: {this.props.pathname}</p>
        <p>search: {this.props.search}</p>
      </PathDebug>
    )
  }
}