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
import { Dispatch, useState, SetStateAction } from "react"
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
import { select } from "redux-saga/effects"

/**
 * Collapse wrapped Send a Request form for each Web Action card
 */
const SendQueryContainer = (
  props: {
    databaseQuery: IDatabaseQueryMetadataAPI
    formData: any
    tag: string
  } & IState &
    IDispatchProps
) => {
  const { databaseQuery, formData, tag } = props
  const url = "/api/.../database/query/run"

  const [isOpenRequestBodyPreview, setIsOpenRequestBodyPreview] = useState(false)
  const [requestBodyFormInputType, setRequestBodyFormInputType] = useState(false)
  const [rawRequestBody, setRawRequestBody] = useState("{}")
  const [formRequestBody, setFormRequestBody] = useState({} as any)
  console.log("FORM REQUEST BODY", formRequestBody)






  const method: HTTPMethod = HTTPMethod.POST

  // Response with fallback to error
  const response = simpleSelectorGet(
    props.simpleRedux,
    [`${tag}::Response`, "data"],
    simpleSelectorGet(props.simpleRedux, [`${tag}::Response`, "error"])
  )

  // Response.data with fallback to error.response
  const responseData = simpleSelectorGet(
    props.simpleRedux,
    [`${tag}::Response`, "data", "data"],
    simpleSelectorGet(props.simpleRedux, [
      `${tag}::Response`,
      "error",
      "response"
    ])
  )

  // Choose whether to use typed form or raw text field to generate request body
  const whichFormData = requestBodyFormInputType
    ? "RAW"
    : "FORM"
  // const formData =
  //   whichFormData === "RAW"
  //     ? rawRequestBody
  //     : formRequestBody
  // TODO fix
  // getFormData(databaseQuery, props.simpleRedux, tag, props.webActionsRaw)

  // Open request section if method has a body
  if (
    methodHasBody(method) &&
    simpleSelectorGet(props.simpleRedux, [
      `${tag}::ButtonRequestBody`,
      "data"
    ]) == undefined
  ) {
    props.simpleMergeData(`${tag}::ButtonRequestBody`, true)
  }

  // Open request body form section if method has body
  if (
    methodHasBody(method) &&
    simpleSelectorGet(props.simpleRedux, [
      `${tag}::ButtonFormRequestBody`,
      "data"
    ]) == undefined
  ) {
    props.simpleMergeData(`${tag}::ButtonFormRequestBody`, true)
  }

  // Open the response section if request has been sent
  if (
    simpleSelectorGet(props.simpleRedux, [`${tag}::ButtonResponse`, "data"]) ==
      undefined &&
    responseData
  ) {
    props.simpleMergeData(`${tag}::ButtonResponse`, true)
  }
  return (
        <div css={cssColumn}>
          <ControlGroup>
            <Button
              css={cssButton}
              large={true}
              onClick={(event: any) => {
                props.simpleMergeData(`${tag}::ButtonRequestBody`, false)
                HTTPMethodDispatch(props)[method](
                  `${tag}::Response`,
                  url,
                  formData
                )
              }}
              intent={HTTPMethodIntent[method]}
              loading={simpleSelectorGet(props.simpleRedux, [
                `${tag}::Response`,
                "loading"
              ])}
              text={"Run Query"}
            />
          </ControlGroup>
          <Menu css={cssMetadataMenu}>
            {methodHasBody(method) ? (
              <MetadataCollapse
                label={`${url}`}
                isOpen={isOpenRequestBodyPreview}
                setIsOpen={setIsOpenRequestBodyPreview}
                text={"Request"}
              >
                <MetadataCopyToClipboard
                  data={formData}
                  description={"Request Body"}
                />
                <CodePreContainer>
                  {JSON.stringify(formData, null, 2)}
                </CodePreContainer>
              </MetadataCollapse>
            ) : (
              <ReduxMetadataCollapse
                content={[]}
                label={`${url}`}
                tag={`${tag}::ButtonRequestBody`}
                text={"Request"}
              />
            )}
            <ReduxMetadataCollapse
              labelElement={
                <StatusTagComponent
                  status={simpleSelectorGet(
                    props.simpleRedux,
                    [`${props.tag}::Response`, "data", "status"],
                    simpleSelectorGet(
                      props.simpleRedux,
                      [`${props.tag}::Response`, "error", "status"],
                      0
                    )
                  )}
                  statusText={simpleSelectorGet(
                    props.simpleRedux,
                    [`${props.tag}::Response`, "data", "statusText"],
                    simpleSelectorGet(
                      props.simpleRedux,
                      [`${props.tag}::Response`, "error", "statusText"],
                      ""
                    )
                  )}
                />
              }
              tag={`${tag}::ButtonResponse`}
              text={"Response"}
            >
              <div>
                <MetadataCopyToClipboard
                  data={responseData}
                  description={"Raw Response"}
                />
                <CodePreContainer>
                  {JSON.stringify(responseData, null, 2)}
                </CodePreContainer>
                <ReduxMetadataCollapse
                  label={"Redux State"}
                  tag={`${tag}::ButtonRawResponse`}
                  text={"Raw Response"}
                >
                  <MetadataCopyToClipboard
                    data={response}
                    description={"Response"}
                  />
                  <CodePreContainer>
                    {JSON.stringify(response, null, 2)}
                  </CodePreContainer>
                </ReduxMetadataCollapse>
              </div>
            </ReduxMetadataCollapse>
          </Menu>
        </div>
  )
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(SendQueryContainer)
