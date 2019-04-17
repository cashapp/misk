import {
  Button,
  Collapse,
  ControlGroup,
  HTMLSelect,
  InputGroup
} from "@blueprintjs/core"
import {
  CodePreContainer,
  FlexContainer,
  HTTPMethodDispatch,
  HTTPMethodIntent
} from "@misk/core"
import { onChangeFnCall, simpleSelect } from "@misk/simpleredux"
import { HTTPMethod } from "http-method-enum"
import * as React from "react"
import styled from "styled-components"
import {
  Metadata,
  MetadataCollapse,
  MetadataMenu,
  RequestFormComponent,
  StatusTagComponent
} from "../components"
import {
  getFormData,
  IDispatchProps,
  IState,
  IWebActionInternal,
  methodHasBody
} from "../ducks"

const Column = styled.div`
  flex-grow: 1;
  flex-basis: 0;
  min-width: 320px;
`

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
        content={"Request Body"}
        label={"Input"}
        tag={`${tag}::ButtonFormRequestBody`}
      >
        <RequestFormComponent {...props} tag={tag} />
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
export const SendRequestCollapseComponent = (
  props: { action: IWebActionInternal; tag: string } & IState & IDispatchProps
) => {
  const { action, tag } = props
  // Determine if Send Request form for the Web Action should be open
  const isOpen = simpleSelect(
    props.simpleForm,
    `${tag}::ButtonSendRequest`,
    "data"
  )
  const url = simpleSelect(props.simpleForm, `${tag}::URL`, "data")
  // Pre-populate the URL field with the action path pattern on open of request form
  if (isOpen && !url) {
    props.simpleFormInput(`${tag}::URL`, action.pathPattern)
  }
  const method: HTTPMethod =
    simpleSelect(props.simpleForm, `${tag}::Method`, "data") ||
    action.dispatchMechanism.reverse()[0]
  const formData = getFormData(
    action,
    props.simpleForm,
    tag,
    props.webActionsRaw
  )
  if (
    methodHasBody(method) &&
    typeof simpleSelect(
      props.simpleForm,
      `${tag}::ButtonRequestBody`,
      "data"
    ) === "string"
  ) {
    props.simpleFormInput(`${tag}::ButtonRequestBody`, true)
  }
  if (
    methodHasBody(method) &&
    typeof simpleSelect(
      props.simpleForm,
      `${tag}::ButtonFormRequestBody`,
      "data"
    ) === "string"
  ) {
    props.simpleFormInput(`${tag}::ButtonFormRequestBody`, true)
  }
  if (
    typeof simpleSelect(props.simpleForm, `${tag}::ButtonResponse`, "data") ===
      "string" &&
    simpleSelect(props.simpleNetwork, `${tag}::Response`, "data")
  ) {
    props.simpleFormInput(`${tag}::ButtonResponse`, true)
  }
  return (
    <Collapse isOpen={isOpen}>
      <InputGroup
        defaultValue={action.pathPattern}
        onChange={onChangeFnCall(props.simpleFormInput, `${tag}::URL`)}
        placeholder={
          "Request URL: absolute ( http://your.url.com/to/send/a/request/to/ ) or internal service endpoint ( /service/web/action )"
        }
        type={"url"}
      />
      <FlexContainer>
        <Column>
          <MetadataMenu>
            <RequestBodyForm
              action={action}
              method={method}
              tag={tag}
              {...props}
            />
          </MetadataMenu>
        </Column>
        <Column>
          <ControlGroup>
            <HTMLSelect
              large={true}
              onChange={onChangeFnCall(props.simpleFormInput, `${tag}::Method`)}
              options={action.dispatchMechanism.sort()}
              value={method}
            />
            <Button
              large={true}
              onClick={(event: any) => {
                props.simpleFormInput(`${tag}::ButtonRequestBody`, false)
                HTTPMethodDispatch(props)[method](
                  `${tag}::Response`,
                  url,
                  isOpen && formData
                )
              }}
              intent={HTTPMethodIntent[method]}
              loading={simpleSelect(
                props.simpleNetwork,
                `${tag}::Response`,
                "loading"
              )}
              text={"Submit"}
            />
          </ControlGroup>
          <MetadataMenu>
            {methodHasBody(method) ? (
              <MetadataCollapse
                {...props}
                content={"Request Body"}
                label={`${url}`}
                tag={`${tag}::ButtonRequestBody`}
              >
                <CodePreContainer>
                  {JSON.stringify(isOpen && formData, null, 2)}
                </CodePreContainer>
              </MetadataCollapse>
            ) : (
              <Metadata content={"Request"} label={`${url}`} {...props} />
            )}
            <MetadataCollapse
              {...props}
              content={"Response"}
              labelElement={
                <StatusTagComponent
                  status={simpleSelect(
                    props.simpleNetwork,
                    `${props.tag}::Response`,
                    "status"
                  )}
                />
              }
              tag={`${tag}::ButtonResponse`}
            >
              <div>
                <CodePreContainer>
                  {JSON.stringify(
                    simpleSelect(
                      props.simpleNetwork,
                      `${tag}::Response`,
                      "data"
                    ),
                    null,
                    2
                  )}
                </CodePreContainer>
                <MetadataCollapse
                  {...props}
                  content={"Raw Response"}
                  label={"Redux State"}
                  tag={`${tag}::ButtonRawResponse`}
                >
                  <CodePreContainer>
                    {JSON.stringify(
                      simpleSelect(props.simpleNetwork, `${tag}::Response`),
                      null,
                      2
                    )}
                  </CodePreContainer>
                </MetadataCollapse>
              </div>
            </MetadataCollapse>
          </MetadataMenu>
        </Column>
      </FlexContainer>
    </Collapse>
  )
}
