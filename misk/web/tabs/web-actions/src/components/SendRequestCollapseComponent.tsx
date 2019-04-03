import {
  Button,
  Collapse,
  ControlGroup,
  HTMLSelect,
  Icon,
  InputGroup,
  Intent,
  Label,
  Pre,
  Tag
} from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import {
  FlexContainer,
  HTTPMethodDispatch,
  HTTPMethodIntent,
  HTTPStatusCodeIntent
} from "@misk/core"
import {
  onChangeFnCall,
  onChangeToggleFnCall,
  onClickFnCall,
  simpleSelect
} from "@misk/simpleredux"
import { HTTPMethod } from "http-method-enum"
import * as React from "react"
import styled from "styled-components"
import { RequestFormComponent } from "../components"
import {
  IDispatchProps,
  IState,
  IWebActionInternal,
  getFormData
} from "../ducks"

const Column = styled.div`
  flex-grow: 1;
  flex-basis: 0;
  min-width: 320px;
`

export const CodePreContainer = styled(Pre)`
  font-family: Fira Code, Menlo !important;
  white-space: pre-wrap !important; /* Since CSS 2.1 */
  white-space: -moz-pre-wrap !important; /* Mozilla, since 1999 */
  white-space: -pre-wrap !important; /* Opera 4-6 */
  white-space: -o-pre-wrap !important; /* Opera 7 */
  word-wrap: break-word !important; /* Internet Explorer 5.5+ */
`

/**
 * Collapse wrapped Send a Request form for each Web Action card
 */
export const SendRequestCollapseComponent = (
  props: { action: IWebActionInternal; tag: string } & IState & IDispatchProps
) => {
  const { tag } = props
  // Determine if Send Request form for the Web Action should be open
  const isOpen =
    simpleSelect(props.simpleForm, `${tag}::Request`, "data") || false
  const url = simpleSelect(props.simpleForm, `${tag}::URL`, "data")
  // Pre-populate the URL field with the action path pattern on open of request form
  if (isOpen && !url) {
    props.simpleFormInput(`${tag}::URL`, props.action.pathPattern)
  }
  const method: HTTPMethod =
    simpleSelect(props.simpleForm, `${tag}::Method`, "data") ||
    props.action.dispatchMechanism.reverse()[0]
  const methodHasBody =
    method === HTTPMethod.PATCH ||
    method === HTTPMethod.POST ||
    method === HTTPMethod.PUT
  return (
    <Collapse isOpen={isOpen}>
      <InputGroup
        defaultValue={props.action.pathPattern}
        onChange={onChangeFnCall(props.simpleFormInput, `${tag}::URL`)}
        placeholder={
          "Request URL: absolute ( http://your.url.com/to/send/a/request/to/ ) or internal service endpoint ( /service/web/action )"
        }
        type={"url"}
      />
      <FlexContainer>
        <Column>
          <Collapse isOpen={methodHasBody}>
            <RequestFormComponent {...props} tag={tag} />
            <br />
          </Collapse>
        </Column>
        <Column>
          <ControlGroup>
            <HTMLSelect
              large={true}
              onChange={onChangeFnCall(props.simpleFormInput, `${tag}::Method`)}
              options={props.action.dispatchMechanism.sort()}
              value={method}
            />
            <Button
              large={true}
              onClick={onClickFnCall(
                HTTPMethodDispatch(props)[method],
                `${tag}::Response`,
                url,
                isOpen &&
                  getFormData(
                    props.action,
                    props.simpleForm,
                    tag,
                    props.webActionsRaw
                  )
              )}
              intent={HTTPMethodIntent[method]}
              loading={simpleSelect(
                props.simpleNetwork,
                `${tag}::Response`,
                "loading"
              )}
              text={"Submit"}
            />
          </ControlGroup>
          <Label>
            Request <Tag>{url}</Tag>
          </Label>
          <Collapse
            isOpen={
              isOpen &&
              getFormData(
                props.action,
                props.simpleForm,
                tag,
                props.webActionsRaw
              )
            }
          >
            <CodePreContainer>
              {JSON.stringify(
                isOpen &&
                  getFormData(
                    props.action,
                    props.simpleForm,
                    tag,
                    props.webActionsRaw
                  ),
                null,
                2
              )}
            </CodePreContainer>
          </Collapse>
          <Collapse
            isOpen={simpleSelect(
              props.simpleNetwork,
              `${tag}::Response`,
              "status"
            )}
          >
            <Label>
              Response{" "}
              <Tag
                intent={HTTPStatusCodeIntent(
                  simpleSelect(
                    props.simpleNetwork,
                    `${tag}::Response`,
                    "status"
                  )[0]
                )}
              >
                {(
                  simpleSelect(
                    props.simpleNetwork,
                    `${tag}::Response`,
                    "status"
                  ) || []
                ).join(" ")}
              </Tag>{" "}
              <Tag
                intent={Intent.NONE}
                onClick={onChangeToggleFnCall(
                  props.simpleFormToggle,
                  `${tag}::ButtonRawResponse`,
                  props.simpleForm
                )}
              >
                <span>
                  Raw Response{" "}
                  {simpleSelect(
                    props.simpleForm,
                    `${tag}::ButtonRawResponse`,
                    "data"
                  ) ? (
                    <Icon icon={IconNames.CARET_DOWN} />
                  ) : (
                    <Icon icon={IconNames.CARET_RIGHT} />
                  )}
                </span>
              </Tag>
            </Label>
          </Collapse>
          <Collapse
            isOpen={simpleSelect(
              props.simpleNetwork,
              `${tag}::Response`,
              "data"
            )}
          >
            <CodePreContainer>
              {JSON.stringify(
                simpleSelect(props.simpleNetwork, `${tag}::Response`, "data"),
                null,
                2
              )}
            </CodePreContainer>
          </Collapse>
          <Collapse
            isOpen={simpleSelect(
              props.simpleForm,
              `${tag}::ButtonRawResponse`,
              "data"
            )}
          >
            <Label>Raw Network Redux State</Label>
            <CodePreContainer>
              {JSON.stringify(
                simpleSelect(props.simpleNetwork, `${tag}::Response`),
                null,
                2
              )}
            </CodePreContainer>
          </Collapse>
        </Column>
      </FlexContainer>
    </Collapse>
  )
}
