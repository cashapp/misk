/** @jsx jsx */
import { Button, ControlGroup, Menu } from "@blueprintjs/core"
import { jsx } from "@emotion/core"
import { CodePreContainer, HTTPMethodIntent } from "@misk/core"
import { HTTPMethodDispatch, simpleSelectorGet } from "@misk/simpleredux"
import { HTTPMethod } from "http-method-enum"
import React, { useState } from "react"
import { connect } from "react-redux"
import {
  cssButton,
  cssColumn,
  cssMetadataMenu,
  ReduxMetadataCollapse,
  MetadataCopyToClipboard,
  StatusTagComponent,
  MetadataCollapse
} from "../components"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps,
  methodHasBody
} from "../ducks"
import { IDatabaseQueryMetadataAPI } from "./DatabaseQueryInterfaces"

/**
 * Collapse wrapped Send a Request form for each Web Action card
 */
const SendQueryContainer = (
  props: {
    databaseQuery: IDatabaseQueryMetadataAPI
    tag: string
  } & IState &
    IDispatchProps
) => {
  const { tag } = props
  const formData = {}
  const url = "/api/.../database/query/run"

  const [isOpenRequestBodyPreview, setIsOpenRequestBodyPreview] = useState(
    false
  )

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
  return React.useMemo(
    () => (
      <div css={cssColumn}>
        <ControlGroup>
          <Button
            css={cssButton}
            large={true}
            onClick={() => {
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
    ),
    [formData]
  )
}

export default connect(mapStateToProps, mapDispatchToProps)(SendQueryContainer)
