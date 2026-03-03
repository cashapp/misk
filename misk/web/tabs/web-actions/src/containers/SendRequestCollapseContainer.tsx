/** @jsx jsx */
import {
  Button,
  Collapse,
  ControlGroup,
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
import { connect } from "react-redux"
import {
  cssButton,
  cssColumn,
  cssMetadataMenu,
  Metadata,
  MetadataCollapse,
  MetadataCopyToClipboard,
  StatusTagComponent
} from "../components"
import { RequestFormContainer } from "../containers"
import {
  getFormData,
  IDispatchProps,
  IState,
  IWebActionInternal,
  mapDispatchToProps,
  mapStateToProps,
  methodHasBody
} from "../ducks"

const RequestBodyForm = (
  props: {
    action: IWebActionInternal
    method: HTTPMethod
    tag: string
  } & IState &
    IDispatchProps
) => {
  const { method, tag } = props
  if (methodHasBody(props.method)) {
    return (
      <MetadataCollapse
        label={"Input"}
        tag={`${tag}::ButtonFormRequestBody`}
        text={"Request Body"}
      >
        <RequestFormContainer {...props} tag={tag} />
      </MetadataCollapse>
    )
  } else {
    return (
      <Metadata
        {...props}
        content={"Request Body"}
        label={`${method} does not have a Body`}
      />
    )
  }
}

/**
 * Collapse wrapped Send a Request form for each Web Action card
 */
const SendRequestCollapseContainer = (
  props: { action: IWebActionInternal; tag: string } & IState & IDispatchProps
) => {
  const { action, tag } = props

  // Determine if Send Request form for the Web Action should be open
  const isOpen = simpleSelectorGet(props.simpleRedux, [
    `${tag}::ButtonSendRequest`,
    "data"
  ])
  const url = simpleSelectorGet(props.simpleRedux, [`${tag}::URL`, "data"])

  // Pre-populate the URL field with the action path pattern on open of request form
  if (isOpen && !url) {
    props.simpleMergeData(`${tag}::URL`, action.pathPattern)
  }
  const method: HTTPMethod =
    simpleSelectorGet(props.simpleRedux, [`${tag}::Method`, "data"]) ||
    action.httpMethod.reverse()[0]

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
      : getFormData(action, props.simpleRedux, tag, props.webActionsRaw)

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
      <InputGroup
        defaultValue={action.pathPattern}
        onChange={onChangeFnCall(props.simpleMergeData, `${tag}::URL`)}
        placeholder={
          "Request URL: absolute ( http://your.url.com/to/send/a/request/to/ ) or internal service endpoint ( /service/web/action )"
        }
        type={"url"}
      />
      <FlexContainer>
        <div css={cssColumn}>
          <Menu css={cssMetadataMenu}>
            <RequestBodyForm
              action={action}
              method={method}
              tag={tag}
              {...props}
            />
          </Menu>
        </div>
        <div css={cssColumn}>
          <ControlGroup>
            <HTMLSelect
              large={true}
              onChange={onChangeFnCall(props.simpleMergeData, `${tag}::Method`)}
              options={action.httpMethod.sort()}
              value={method}
            />
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
              text={"Submit"}
            />
          </ControlGroup>
          <Menu css={cssMetadataMenu}>
            {methodHasBody(method) ? (
              <MetadataCollapse
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
              </MetadataCollapse>
            ) : (
              <MetadataCollapse
                content={[]}
                label={`${url}`}
                tag={`${tag}::ButtonRequestBody`}
                text={"Request"}
              />
            )}
            <MetadataCollapse
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
                <MetadataCollapse
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
                </MetadataCollapse>
              </div>
            </MetadataCollapse>
          </Menu>
        </div>
      </FlexContainer>
    </Collapse>
  )
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(SendRequestCollapseContainer)
