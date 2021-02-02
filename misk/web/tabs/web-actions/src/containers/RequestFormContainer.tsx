import * as React from "react"
import { connect } from "react-redux"
import { RequestFormFieldBuilderContainer } from "../containers"
import {
  findIndexAction,
  IDispatchProps,
  IState,
  IWebActionInternal,
  mapDispatchToProps,
  mapStateToProps
} from "../ducks"

export const RequestFormContainer = (
  props: {
    action: IWebActionInternal
    tag: string
  } & IState &
    IDispatchProps
) => {
  const { action } = props
  const { typesMetadata } = props.webActionsRaw.get("metadata")[
    findIndexAction(action, props.webActionsRaw)
  ]
  return (
    <RequestFormFieldBuilderContainer
      action={action}
      id={"0"}
      tag={props.tag}
      typesMetadata={typesMetadata}
    />
  )
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(RequestFormContainer)
