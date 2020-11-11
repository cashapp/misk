/** @jsx jsx */
import {
  Button,
  Collapse,
  ControlGroup,
  H5,
  HTMLSelect,
  InputGroup,
  Menu
} from "@blueprintjs/core"
import { jsx } from "@emotion/core"
import { CodePreContainer, FlexContainer, HTTPMethodIntent } from "@misk/core"
import {
  HTTPMethodDispatch,
  onChangeFnCall,
  simpleSelectorGet
} from "@misk/simpleredux"
import { HTTPMethod } from "http-method-enum"
import React, { Dispatch, useReducer, useState, SetStateAction } from "react"
import { connect } from "react-redux"
import {
  cssButton,
  cssColumn,
  cssMetadataMenu,
  Metadata,
  ReduxMetadataCollapse,
  MetadataCopyToClipboard,
  StatusTagComponent,
  MetadataCollapse
} from "../components"
import { FormBuilderContainer } from "../form-builder"
import {
  getFormData,
  IDispatchProps,
  IState,
  IWebActionInternal,
  mapDispatchToProps,
  mapStateToProps,
  methodHasBody
} from "../ducks"
import {
  IConstraintMetadata,
  IDatabaseQueryMetadataAPI,
  IOrderMetadata,
  ISelectMetadata
} from "./DatabaseQueryInterfaces"
import { SendQueryContainer } from "../containers"
import { select } from "redux-saga/effects"
import { QueryFormContainer } from './QueryFormContainer'

export const SetFormData = React.createContext(null);

/**
 * Collapse wrapped Send a Request form for each Web Action card
 */
export const RunQueryCollapseContainer = (
  props: {
    databaseQuery: IDatabaseQueryMetadataAPI
    isOpen: boolean
    tag: string
  }
) => {
  const { databaseQuery, tag } = props
  const url = "/api/.../database/query/run"

  // Determine if Send Request form for the Web Action should be open
  const isOpen = props.isOpen

  const [isOpenRequestBodyPreview, setIsOpenRequestBodyPreview] = useState(false)
  const [requestBodyFormInputType, setRequestBodyFormInputType] = useState(false)
  const [rawRequestBody, setRawRequestBody] = useState("{}")
  const [formData, setFormData] = useState({} as any)


  // const formData ={}




  const method: HTTPMethod = HTTPMethod.POST

  // // Response with fallback to error
  // const response = simpleSelectorGet(
  //   props.simpleRedux,
  //   [`${tag}::Response`, "data"],
  //   simpleSelectorGet(props.simpleRedux, [`${tag}::Response`, "error"])
  // )

  // // Response.data with fallback to error.response
  // const responseData = simpleSelectorGet(
  //   props.simpleRedux,
  //   [`${tag}::Response`, "data", "data"],
  //   simpleSelectorGet(props.simpleRedux, [
  //     `${tag}::Response`,
  //     "error",
  //     "response"
  //   ])
  // )

  // // Choose whether to use typed form or raw text field to generate request body
  // const whichFormData = requestBodyFormInputType
  //   ? "RAW"
  //   : "FORM"
  // const formData =
  //   whichFormData === "RAW"
  //     ? rawRequestBody
  //     : {"test": 'yo'}
  //     // : formRequestBody
  // // TODO fix
  // // getFormData(databaseQuery, props.simpleRedux, tag, props.webActionsRaw)

  // // Open request section if method has a body
  // if (
  //   methodHasBody(method) &&
  //   simpleSelectorGet(props.simpleRedux, [
  //     `${tag}::ButtonRequestBody`,
  //     "data"
  //   ]) == undefined
  // ) {
  //   props.simpleMergeData(`${tag}::ButtonRequestBody`, true)
  // }

  // // Open request body form section if method has body
  // if (
  //   methodHasBody(method) &&
  //   simpleSelectorGet(props.simpleRedux, [
  //     `${tag}::ButtonFormRequestBody`,
  //     "data"
  //   ]) == undefined
  // ) {
  //   props.simpleMergeData(`${tag}::ButtonFormRequestBody`, true)
  // }

  // // Open the response section if request has been sent
  // if (
  //   simpleSelectorGet(props.simpleRedux, [`${tag}::ButtonResponse`, "data"]) ==
  //     undefined &&
  //   responseData
  // ) {
  //   props.simpleMergeData(`${tag}::ButtonResponse`, true)
  // }
  return (
    <Collapse isOpen={isOpen}>
      <FlexContainer>
      <SetFormData.Provider value={setFormData}>

        <QueryFormContainer
        databaseQuery={databaseQuery}
        />
        <SendQueryContainer
        databaseQuery={databaseQuery}
        formData={formData}
        tag={tag}
         />
         </SetFormData.Provider>
      </FlexContainer>
    </Collapse>
  )
}
