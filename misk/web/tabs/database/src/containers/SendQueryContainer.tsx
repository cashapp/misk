/** @jsx jsx */
import { Button, ControlGroup, Menu } from "@blueprintjs/core"
import { jsx } from "@emotion/react"
import { CodePreContainer, HTTPMethodIntent } from "@misk/core"
import { HTTPMethodDispatch, simpleSelectorGet } from "@misk/simpleredux"
import { HTTPMethod } from "http-method-enum"
import { Dispatch, SetStateAction } from "react"
import { connect } from "react-redux"
import {
  cssButton,
  cssColumn,
  cssMetadataMenu,
  ReduxMetadataCollapse,
  MetadataCopyToClipboard,
  StatusTagComponent,
  MetadataCollapse,
  QueryResultTableComponent,
} from "../components"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps,
} from "../ducks"
import {
  IDatabaseQueryMetadataAPI,
  IRunQueryAPIRequest,
  IRunQueryAPIResponse,
} from "./DatabaseInterfaces"

/**
 * Collapse wrapped Send a Request form for each Web Action card
 */
const SendQueryContainer = (
  props: {
    databaseQuery: IDatabaseQueryMetadataAPI
    formData: any
    isOpenRequestBodyPreview: boolean
    setIsOpenRequestBodyPreview: Dispatch<SetStateAction<boolean>>
    tag: string
  } & IState &
    IDispatchProps
) => {
  const {
    databaseQuery,
    formData,
    isOpenRequestBodyPreview,
    setIsOpenRequestBodyPreview,
    tag,
  } = props
  const url = databaseQuery.queryWebActionPath
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
      "response",
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
    simpleSelectorGet(props.simpleRedux, [
      `${tag}::ButtonRequestBody`,
      "data",
    ]) == undefined
  ) {
    props.simpleMergeData(`${tag}::ButtonRequestBody`, true)
  }

  // Open request body form section if method has body
  if (
    simpleSelectorGet(props.simpleRedux, [
      `${tag}::ButtonFormRequestBody`,
      "data",
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
          onClick={() => {
            props.simpleMergeData(`${tag}::ButtonRequestBody`, false)
            HTTPMethodDispatch(props)[method](`${tag}::Response`, url, {
              entityClass: databaseQuery.entityClass,
              queryClass: databaseQuery.queryClass,
              query: formData || {},
            } as IRunQueryAPIRequest)
          }}
          intent={HTTPMethodIntent[method]}
          loading={simpleSelectorGet(props.simpleRedux, [
            `${tag}::Response`,
            "loading",
          ])}
          text={"Run Query"}
        />
      </ControlGroup>
      <Menu css={cssMetadataMenu}>
        <MetadataCollapse
          label={`${url}`}
          isOpen={isOpenRequestBodyPreview}
          setIsOpen={setIsOpenRequestBodyPreview}
          text={"Query"}
        >
          <MetadataCopyToClipboard
            data={formData}
            description={"Request Body"}
          />
          <CodePreContainer>
            {JSON.stringify(formData, null, 2)}
          </CodePreContainer>
        </MetadataCollapse>
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
          text={"Results"}
        >
          <div>
            <QueryResultTableComponent
              response={responseData as IRunQueryAPIResponse}
            />
            <MetadataCopyToClipboard
              data={responseData}
              description={"Query Results"}
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

export default connect(mapStateToProps, mapDispatchToProps)(SendQueryContainer)
