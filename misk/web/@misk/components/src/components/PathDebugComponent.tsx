import * as React from "react"

/**
 * <PathDebugComponent
 *    hash={props.location.hash}
 *    pathname={props.location.pathname}
 *    search={props.location.search}
 * />
 */

export interface IPathDebugProps {
  hash: string
  pathname: string
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
