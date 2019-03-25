import {
  Button,
  Collapse,
  ControlGroup,
  HTMLSelect,
  Icon,
  InputGroup,
  Intent,
  Label,
  Tag,
  TextArea
} from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { CodePreContainer } from "@misk/core"
import {
  onChangeFnCall,
  onChangeToggleFnCall,
  onClickFnCall,
  simpleSelect
} from "@misk/simpleredux"
import { HTTPMethod } from "http-method-enum"
import * as React from "react"
import {
  HTTPMethodDispatch,
  HTTPMethodIntent,
  HTTPStatusCodeIntent,
  IDispatchProps,
  IState,
  IWebActionInternal
} from "../ducks"

/**
 * Collapse wrapped Send a Request form for each Web Action card
 */
export const SendRequestCollapseComponent = (
  props: { action: IWebActionInternal; tag: string } & IState & IDispatchProps
) => {
  // Determine if Send Request form for the Web Action should be open
  const isOpen =
    simpleSelect(
      props.simpleForm,
      `${props.tag}::${props.action.pathPattern}::Request`,
      "data"
    ) || false
  const url = simpleSelect(
    props.simpleForm,
    `${props.tag}::${props.action.pathPattern}::URL`,
    "data"
  )
  // Pre-populate the URL field with the action path pattern on open of request form
  if (isOpen && !url) {
    props.simpleFormInput(
      `${props.tag}::${props.action.pathPattern}::URL`,
      props.action.pathPattern
    )
  }
  const actionTag = `${props.tag}::${props.action.function}::${
    props.action.pathPattern
  }`
  const method: HTTPMethod =
    simpleSelect(props.simpleForm, `${actionTag}::Method`, "data") ||
    props.action.dispatchMechanism.reverse()[0]
  const methodHasBody =
    method === HTTPMethod.PATCH ||
    method === HTTPMethod.POST ||
    method === HTTPMethod.PUT
  return (
    <Collapse isOpen={isOpen}>
      <InputGroup
        defaultValue={props.action.pathPattern}
        onChange={onChangeFnCall(props.simpleFormInput, `${actionTag}::URL`)}
        placeholder={
          "Request URL: absolute ( http://your.url.com/to/send/a/request/to/ ) or internal service endpoint ( /service/web/action )"
        }
        type={"url"}
      />
      <Collapse isOpen={methodHasBody}>
        <TextArea
          fill={true}
          onChange={onChangeFnCall(props.simpleFormInput, `${actionTag}::Body`)}
          placeholder={
            "Request Body (JSON or Text).\nDrag bottom right corner of text area input to expand."
          }
        />
      </Collapse>
      <ControlGroup>
        <HTMLSelect
          large={true}
          onChange={onChangeFnCall(
            props.simpleFormInput,
            `${actionTag}::Method`
          )}
          options={props.action.dispatchMechanism.sort()}
          value={method}
        />
        <Button
          large={true}
          onClick={onClickFnCall(
            HTTPMethodDispatch(props)[method],
            `${actionTag}::Response`,
            url,
            simpleSelect(props.simpleForm, `${actionTag}::Body`, "data")
          )}
          intent={HTTPMethodIntent[method]}
          loading={simpleSelect(
            props.simpleNetwork,
            `${actionTag}::Response`,
            "loading"
          )}
          text={"Submit"}
        />
      </ControlGroup>
      <Label>
        Request <Tag>{url}</Tag>
      </Label>
      <Collapse
        isOpen={simpleSelect(props.simpleForm, `${actionTag}::Body`, "data")}
      >
        <CodePreContainer>
          {JSON.stringify(
            simpleSelect(props.simpleForm, `${actionTag}::Body`, "data"),
            null,
            2
          )}
        </CodePreContainer>
      </Collapse>
      <Collapse
        isOpen={simpleSelect(
          props.simpleNetwork,
          `${actionTag}::Response`,
          "status"
        )}
      >
        <Label>
          Response{" "}
          <Tag
            intent={HTTPStatusCodeIntent(
              simpleSelect(
                props.simpleNetwork,
                `${actionTag}::Response`,
                "status"
              )[0]
            )}
          >
            {(
              simpleSelect(
                props.simpleNetwork,
                `${actionTag}::Response`,
                "status"
              ) || []
            ).join(" ")}
          </Tag>{" "}
          <Tag
            intent={Intent.NONE}
            onClick={onChangeToggleFnCall(
              props.simpleFormToggle,
              `${actionTag}::ButtonRawResponse`,
              props.simpleForm
            )}
          >
            <span>
              Raw Response{" "}
              {simpleSelect(
                props.simpleForm,
                `${actionTag}::ButtonRawResponse`,
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
          `${actionTag}::Response`,
          "data"
        )}
      >
        <CodePreContainer>
          {JSON.stringify(
            simpleSelect(props.simpleNetwork, `${actionTag}::Response`, "data"),
            null,
            2
          )}
        </CodePreContainer>
      </Collapse>
      <Collapse
        isOpen={simpleSelect(
          props.simpleForm,
          `${actionTag}::ButtonRawResponse`,
          "data"
        )}
      >
        <Label>Raw Network Redux State</Label>
        <CodePreContainer>
          {JSON.stringify(
            simpleSelect(props.simpleNetwork, `${actionTag}::Response`),
            null,
            2
          )}
        </CodePreContainer>
      </Collapse>
    </Collapse>
  )
}
