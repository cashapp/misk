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
  StatusTagComponent
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
import { IDatabaseQueryMetadataAPI } from "./DatabaseQueryInterfaces"

/**
 * Collapse wrapped Send a Request form for each Web Action card
 */
const RunQueryCollapseContainer = (
  props: {
    databaseQuery: IDatabaseQueryMetadataAPI
    isOpen: boolean
    tag: string
  } & IState &
    IDispatchProps
) => {
  const { databaseQuery, tag } = props
  const MISK_RUN_QUERY_ENDPOINT = "/api/database/query/run......"
  const url = simpleSelectorGet(props.simpleRedux, [`${tag}::URL`, "data"])

  // Determine if Send Request form for the Web Action should be open
  const isOpen = props.isOpen

  // Pre-populate the URL field with the action path pattern on open of request form
  if (isOpen && !url) {
    props.simpleMergeData(`${tag}::URL`, MISK_RUN_QUERY_ENDPOINT)
  }
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
  const whichFormData = simpleSelectorGet(
    props.simpleRedux,
    [`${tag}::RequestBodyFormInputType`, "data"],
    false
  )
    ? "RAW"
    : "FORM"
  const formData =
    whichFormData === "RAW"
      ? simpleSelectorGet(props.simpleRedux, [`${tag}::RawRequestBody`, "data"])
      : {}
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
    <Collapse isOpen={isOpen}>
      <FlexContainer>
        <div css={cssColumn}>
          <Menu css={cssMetadataMenu}>
            <H5>{"Constraints"}</H5>
            <FormBuilderContainer
              formType={"root form type goes here"}
              noFormIdentifier={`${databaseQuery.queryClass} Constraints`}
              types={databaseQuery.constraints}
            />
            <H5>{"Orders"}</H5>
            <FormBuilderContainer
              formType={"root form type goes here"}
              noFormIdentifier={`${databaseQuery.queryClass} Orders`}
              types={databaseQuery.orders}
            />
            <H5>{"Selects"}</H5>
            <FormBuilderContainer
              formType={"root form type goes here"}
              noFormIdentifier={`${databaseQuery.queryClass} Selects`}
              types={databaseQuery.selects}
            />
          </Menu>
        </div>
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
                  isOpen && formData
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
              <ReduxMetadataCollapse
                label={`${url}`}
                tag={`${tag}::ButtonRequestBody`}
                text={"Request"}
              >
                <MetadataCopyToClipboard
                  data={formData}
                  description={"Request Body"}
                />
                <CodePreContainer>
                  {JSON.stringify(isOpen && formData, null, 2)}
                </CodePreContainer>
              </ReduxMetadataCollapse>
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
      </FlexContainer>
    </Collapse>
  )
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(RunQueryCollapseContainer)
