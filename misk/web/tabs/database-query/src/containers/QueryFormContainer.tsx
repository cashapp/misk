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
import { Dispatch, useContext, useState, SetStateAction } from "react"
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
import { SetFormData, SendQueryContainer } from "../containers"
import { select } from "redux-saga/effects"

/**
 * Collapse wrapped Send a Request form for each Web Action card
 */
export const QueryFormContainer = (
  props: {
    databaseQuery: IDatabaseQueryMetadataAPI
  }
) => {
  const { databaseQuery } = props
  const setFormData = useContext(SetFormData)

  const [isOpenRequestBodyPreview, setIsOpenRequestBodyPreview] = useState(false)
  const [requestBodyFormInputType, setRequestBodyFormInputType] = useState(false)
  const [rawRequestBody, setRawRequestBody] = useState("{}")

  return (
        <div css={cssColumn}>
          <Menu css={cssMetadataMenu}>
            <H5>{"Query"}</H5>
            <FormBuilderContainer
              formType={"QueryRequest"}
              noFormIdentifier={`QueryRequest`}
              types={databaseQuery.types}
              rawRequestBody={rawRequestBody}
              setRawRequestBody={setRawRequestBody}
              requestBodyFormInputType={requestBodyFormInputType}
              setRequestBodyFormInputType={setRequestBodyFormInputType}
              setIsOpenRequestBodyPreview={setIsOpenRequestBodyPreview}
              setFormData={setFormData}
            />

            {/* {databaseQuery.orders.length > 0 && <H5>{"Orders"}</H5>}
            {databaseQuery.orders.map((order: IOrderMetadata) => (
              <FormBuilderContainer
                formType={order.parametersType}
                noFormIdentifier={`${databaseQuery.queryClass} ${order.name}`}
                types={databaseQuery.types}
                rawRequestBody={rawRequestBody}
                  setRawRequestBody={setRawRequestBody}
                  requestBodyFormInputType={requestBodyFormInputType}
                  setRequestBodyFormInputType={setRequestBodyFormInputType}
                  setIsOpenRequestBodyPreview={setIsOpenRequestBodyPreview}
                  setFormData={setFormRequestBody}
              />
            ))}
            {databaseQuery.selects.length > 0 && <H5>{"Selects"}</H5>}
            {databaseQuery.selects.map((select: ISelectMetadata) => (
              <FormBuilderContainer
                formType={select.parametersType}
                noFormIdentifier={`${databaseQuery.queryClass} ${select.name}`}
                types={databaseQuery.types}
                rawRequestBody={rawRequestBody}
                  setRawRequestBody={setRawRequestBody}
                  requestBodyFormInputType={requestBodyFormInputType}
                  setRequestBodyFormInputType={setRequestBodyFormInputType}
                  setIsOpenRequestBodyPreview={setIsOpenRequestBodyPreview}
                  setFormData={setFormRequestBody}
              />
            ))} */}
          </Menu>
        </div>

  )
}
