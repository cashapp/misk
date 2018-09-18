import * as React from "react"

export interface IPathDebugProps {
  hash: string,
  pathname: string,
  search: string
}

export const PathDebugComponent = (props: IPathDebugProps) => {
  return (
    <div>
      <p>hash: {props.hash}</p>
      <p>pathname: {props.pathname}</p>
      <p>search: {props.search}</p>
    </div>
  )
}