import * as React from "react"
import styled from "styled-components"

export interface IComponentProps {
  data: IData
}

interface IData {
  userId: number
  id: number
  title: string
  body: string
}

const Container = styled.div``

export default class TabComponent extends React.PureComponent<IComponentProps> {
  renderExample(data: IData) {
    return (
      <div>
        <h5>Title: {data.title}</h5>
        <p>Post ID: {data.id}</p>
        <p>Author ID: {data.userId}</p>
        <p>{data.body}</p>
        <hr />
      </div>
    )
  }

  render() {
    const { data } = this.props
    return (
      <Container>
        <h1>Example</h1>
        <p>{status}</p>
        {data &&
          Object.entries(data).map(([, value]) => this.renderExample(value))}
      </Container>
    )
  }
}
