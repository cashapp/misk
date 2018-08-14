import React from "react"

export interface IPathDebugProps {
  hash: string,
  pathname: string,
  search: string
}

const PathDebugComponent = (props: IPathDebugProps) => {
  return (
    <div>
      <p>hash: {props.hash}</p>
      <p>pathname: {props.pathname}</p>
      <p>search: {props.search}</p>
    </div>
  )
}

export { PathDebugComponent }