import { Pre, Classes, H1, HTMLTable } from "@blueprintjs/core"
import { ErrorCalloutComponent } from "@misk/core"
import * as React from "react"

export interface ITableProps {
  data: any
  rows?: number
  url?: string
  tag?: string
}

const Row = (props: ITableProps) => {
  const { data } = props
  return <tr>{data && Object.entries(data).map(([k, v]) => <td>{v}</td>)}</tr>
}

const Rows = (props: ITableProps) => {
  const { data, rows } = props
  return (
    <tbody>
      {data && data.slice(0, rows).map((row: any) => <Row data={row} />)}
    </tbody>
  )
}

const Heading = (props: ITableProps) => {
  const { data } = props
  return (
    <thead>
      <tr>
        {data && Object.entries(data).map(([k, v]) => <th key={k}>{k}</th>)}
      </tr>
    </thead>
  )
}

export const SampleTableComponent = (props: ITableProps) => {
  /**
   * Destructure props for easier usage: data instead of props.data
   */
  const { data, rows = 5, url, tag } = props
  const name = `Sample Table Component :: ${tag}`
  /**
   * Have a nice failure mode while your data is loading or doesn't load
   */
  if (!data.cars || data.cars === null) {
    const FakeCell = <p className={Classes.SKELETON}>lorem ipsum 1234 5678</p>
    return (
      <div>
        <H1>{name}</H1>
        <Pre>url: {url}</Pre>
        <HTMLTable bordered={true} striped={true}>
          <thead>
            <tr>
              <th>{FakeCell}</th>
              <th>{FakeCell}</th>
              <th>{FakeCell}</th>
              <th>{FakeCell}</th>
              <th>{FakeCell}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>{FakeCell}</td>
              <td>{FakeCell}</td>
              <td>{FakeCell}</td>
              <td>{FakeCell}</td>
              <td>{FakeCell}</td>
            </tr>
          </tbody>
        </HTMLTable>
        <ErrorCalloutComponent error={data.error} />
      </div>
    )
  } else {
    /**
     * Data is loaded and ready to be rendered
     */
    const tableData = data.cars
    return (
      <div>
        <H1>{name}</H1>
        <Pre>url: {url}</Pre>
        <HTMLTable bordered={true} striped={true}>
          <Heading data={tableData[0]} />
          <Rows data={tableData} rows={rows} />
        </HTMLTable>
      </div>
    )
  }
}

export default SampleTableComponent
