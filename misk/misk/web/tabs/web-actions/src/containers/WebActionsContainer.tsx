import {
  Button,
  Card,
  Classes,
  Collapse,
  ControlGroup,
  H3,
  H5,
  HTMLSelect,
  Icon,
  InputGroup,
  Intent,
  Label,
  Menu,
  MenuItem,
  Spinner,
  Tag,
  TextArea,
  Tooltip
} from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { CodePreContainer, FlexContainer } from "@misk/core"
import {
  onChangeFnCall,
  onChangeToggleFnCall,
  onClickFnCall,
  simpleSelect
} from "@misk/simpleredux"
import { chain } from "lodash"
import { HTTPMethod } from "http-method-enum"
import * as React from "react"
import { connect } from "react-redux"
import styled from "styled-components"
import {
  IDispatchProps,
  IState,
  IWebActionInternal,
  rootDispatcher,
  rootSelectors,
  WebActionInternalLabel
} from "../ducks"

const tag = "WebActions"
const filterTag = `${tag}::Filter`

//TODO(adrw) upstream to @misk/core
const HTTPMethodIntent: { [method in HTTPMethod]: Intent } = {
  [HTTPMethod.CONNECT]: Intent.DANGER,
  [HTTPMethod.DELETE]: Intent.DANGER,
  [HTTPMethod.GET]: Intent.PRIMARY,
  [HTTPMethod.HEAD]: Intent.WARNING,
  [HTTPMethod.OPTIONS]: Intent.NONE,
  [HTTPMethod.PATCH]: Intent.SUCCESS,
  [HTTPMethod.POST]: Intent.SUCCESS,
  [HTTPMethod.PUT]: Intent.SUCCESS,
  [HTTPMethod.TRACE]: Intent.NONE
}

const HTTPStatusCodeIntent = (code: number) => {
  if (200 <= code && code < 300) {
    return Intent.SUCCESS
  } else if (300 <= code && code < 400) {
    return Intent.PRIMARY
  } else if (400 <= code && code < 500) {
    return Intent.WARNING
  } else if (500 <= code && code < 600) {
    return Intent.DANGER
  } else {
    return Intent.NONE
  }
}

const HTTPMethodDispatch: any = (props: IDispatchProps) => ({
  [HTTPMethod.CONNECT]: props.simpleNetworkGet,
  [HTTPMethod.DELETE]: props.simpleNetworkDelete,
  [HTTPMethod.GET]: props.simpleNetworkGet,
  [HTTPMethod.HEAD]: props.simpleNetworkHead,
  [HTTPMethod.OPTIONS]: props.simpleNetworkGet,
  [HTTPMethod.PATCH]: props.simpleNetworkPatch,
  [HTTPMethod.POST]: props.simpleNetworkPost,
  [HTTPMethod.PUT]: props.simpleNetworkPut,
  [HTTPMethod.TRACE]: props.simpleNetworkGet
})

const FloatLeft = styled.span`
  float: left;
  margin: 0 10px 0 0;
`

const FloatRight = styled.span`
  float: right;
  margin: 0 0 0 10px;
`

const CodeTag = styled(Tag)`
  font-family: monospace;
`

const Header = styled.div`
  display: block;
  height: 36px;
`

const Column = styled.div`
  flex-grow: 1;
  flex-basis: 0;
  min-width: 320px;
`

const MetadataMenu = styled(Menu)`
  li {
    margin-bottom: 0;
  }
`

/**
 * Used in rendering the Content Types metadata request -> response
 */
const RequestResponeContentTypes = (props: { action: IWebActionInternal }) => (
  <span>
    <span>{props.action.requestMediaTypes}</span>{" "}
    <Icon icon={IconNames.ARROW_RIGHT} />{" "}
    <span>{props.action.responseMediaType}</span>
  </span>
)

/**
 * Single cell component for a piece of metadata
 * @param props
 *  * content: primary content that is displayed (ie. eng, service-owners)
 *  * label: right aligned faded text that is a label for the data (ie. Roles)
 *  * tooltip: text displayed in tooltip when content is hovered over.
 *      Separate definition from content in the case that a truncated length
 *      is displayed but the full length of content is displayed in tooltip
 */
const Metadata = (
  props: {
    content: string | JSX.Element
    label: string
    tooltip?: string | JSX.Element
  } & any
) => {
  if (props.tooltip) {
    return (
      <MenuItem
        label={props.label}
        text={<Tooltip content={props.tooltip}>{props.content}</Tooltip>}
        {...props}
      />
    )
  } else {
    return (
      <MenuItem
        label={props.label}
        text={<Tooltip>{props.content}</Tooltip>}
        {...props}
      />
    )
  }
}

/**
 * Metadata that slides out content below when clicked
 * @param props : includes same props as Metadata with a few additional
 *  * children: any components to display when the Metadata is clicked
 *  * tag: string to use in @misk/SimpleRedux/SimpleForm to register Metadata clicks
 *  * IState: include connected State from parent container
 *      Provides access to @misk/SimpleRedux/SimpleForm substate in Redux
 *  * IDispatchProps: include connected dispatch object from parent container
 *      Provides access to @misk/SimpleRedux/SimpleForm input handlers
 */
const MetadataCollapse = (
  props: {
    children: any
    content: string | JSX.Element
    label: string
    tag: string
    tooltip?: string | JSX.Element
  } & IState &
    IDispatchProps
) => (
  <div>
    <Metadata
      content={
        <span>
          {simpleSelect(props.simpleForm, props.tag, "data") ? (
            <Icon icon={IconNames.CARET_DOWN} />
          ) : (
            <Icon icon={IconNames.CARET_RIGHT} />
          )}{" "}
          {props.content}
        </span>
      }
      label={props.label}
      onClick={onChangeToggleFnCall(
        props.simpleFormToggle,
        props.tag,
        props.simpleForm
      )}
    />
    <Collapse isOpen={simpleSelect(props.simpleForm, props.tag, "data")}>
      {props.children}
    </Collapse>
  </div>
)

/**
 * Renders HTTP Method tags for each Web Action card
 */
const MethodTag = (props: { method: HTTPMethod }) => (
  <FloatRight>
    <Tag large={true} intent={HTTPMethodIntent[props.method]}>
      {props.method}
    </Tag>
  </FloatRight>
)

/**
 * Collapse wrapped Send a Request form for each Web Action card
 */
const SendRequestCollapse = (
  props: { action: IWebActionInternal } & IState & IDispatchProps
) => {
  // Determine if Send Request form for the Web Action should be open
  const isOpen =
    simpleSelect(
      props.simpleForm,
      `${tag}::${props.action.pathPattern}::Request`,
      "data"
    ) || false
  const url = simpleSelect(
    props.simpleForm,
    `${tag}::${props.action.pathPattern}::URL`,
    "data"
  )
  // Pre-populate the URL field with the action path pattern on open of request form
  if (isOpen && !url) {
    props.simpleFormInput(
      `${tag}::${props.action.pathPattern}::URL`,
      props.action.pathPattern
    )
  }
  const actionTag = `${tag}::${props.action.function}::${
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

/**
 * Web Action Card rendered for each bound Web Action
 */
const WebAction = (
  props: { action: IWebActionInternal } & IState & IDispatchProps
) => (
  <div>
    <Card>
      <Header>
        {props.action.dispatchMechanism.map(m => (
          <MethodTag method={m} />
        ))}
        <FloatLeft>
          <H3>{props.action.name}</H3>
        </FloatLeft>
        <FloatLeft>
          <CodeTag large={true}>{props.action.pathPattern}</CodeTag>
        </FloatLeft>
      </Header>
      {props.action.nonAccessOrTypeFunctionAnnotations.map(a => (
        <H5>{a}</H5>
      ))}
      <FlexContainer>
        <Column>
          <MetadataMenu>
            <Metadata
              content={props.action.function}
              label={"Function"}
              tooltip={props.action.function}
            />
            <Metadata
              content={props.action.allowedServices}
              label={"Services"}
              tooltip={props.action.allowedServices}
            />
            <Metadata
              content={props.action.allowedRoles}
              label={"Roles"}
              tooltip={props.action.allowedRoles}
            />
            <Metadata
              content={props.action.authFunctionAnnotations[0]}
              label={"Access"}
              tooltip={props.action.authFunctionAnnotations[0]}
            />
          </MetadataMenu>
        </Column>
        <Column>
          <MetadataMenu>
            <Metadata
              content={<RequestResponeContentTypes action={props.action} />}
              label={"Content Types"}
              tooltip={<RequestResponeContentTypes action={props.action} />}
            />
            <MetadataCollapse
              content={"Application Interceptors"}
              label={`(${props.action.applicationInterceptors.length})`}
              tag={`${tag}::${
                props.action.pathPattern
              }::ApplicationInterceptors`}
              {...props}
            >
              {props.action.applicationInterceptors.map(i => (
                <MenuItem text={<Tooltip content={i}>{i}</Tooltip>} />
              ))}
            </MetadataCollapse>
            <MetadataCollapse
              content={"Network Interceptors"}
              label={`(${props.action.networkInterceptors.length})`}
              tag={`${tag}::${props.action.pathPattern}::NetworkInterceptors`}
              {...props}
            >
              {props.action.networkInterceptors.map(i => (
                <MenuItem text={<Tooltip content={i}>{i}</Tooltip>} />
              ))}
            </MetadataCollapse>
            <MetadataCollapse
              content={"Send a Request"}
              label={""}
              tag={`${tag}::${props.action.pathPattern}::Request`}
              {...props}
            >
              <span />
            </MetadataCollapse>
          </MetadataMenu>
        </Column>
      </FlexContainer>
      <SendRequestCollapse {...props} />
    </Card>
    <br />
  </div>
)

/**
 * Loops over bound Web Actions array (metadata) and renders Web Action card
 */
const WebActions = (
  props: { metadata: IWebActionInternal[] } & IState & IDispatchProps
) => {
  if (props.metadata) {
    return (
      <div>
        {props.metadata.map((action: any) => (
          <WebAction action={action} {...props} />
        ))}
      </div>
    )
  } else {
    return <Spinner />
  }
}

const FilterWebActions = (props: IState & IDispatchProps) => {
  const FilterSelectOptions = [...Object.keys(WebActionInternalLabel)]
  return (
    <div>
      <ControlGroup fill={true}>
        <HTMLSelect
          large={true}
          onChange={onChangeFnCall(
            props.simpleFormInput,
            `${filterTag}::HTMLSelect`
          )}
          options={FilterSelectOptions}
        />
        <InputGroup
          large={true}
          onChange={onChangeFnCall(
            props.simpleFormInput,
            `${filterTag}::Input`
          )}
          placeholder={"Filter Web Actions"}
        />
        {/* TODO(adrw) Use multiselect to do autocomplete filters */}
      </ControlGroup>
    </div>
  )
}

/**
 * Empty Web Action Card UI for use with BlueprintJS Skeleton class in loading UIs
 * https://blueprintjs.com/docs/#core/components/skeleton
 */
const SkeletonText = () => (
  <span className={Classes.SKELETON}>{"Lorem ipsum"}</span>
)

const SkeletonWebActions = () => (
  <Card>
    <Header>
      {[HTTPMethod.GET].map(m => (
        <MethodTag method={m} />
      ))}
      <FloatLeft>
        <H3 className={Classes.SKELETON}>{"AnotherSimpleWebAction"}</H3>
      </FloatLeft>
      <FloatLeft>
        <CodeTag large={true}>{<SkeletonText />}</CodeTag>
      </FloatLeft>
    </Header>
    <FlexContainer>
      <Column>
        <MetadataMenu>
          <Metadata label={"Function"} content={<SkeletonText />} />
          <Metadata label={"Services"} content={<SkeletonText />} />
          <Metadata label={"Roles"} content={<SkeletonText />} />
          <Metadata label={"Access"} content={<SkeletonText />} />
        </MetadataMenu>
      </Column>
      <Column>
        <MetadataMenu>
          <Metadata label={"Content Types"} content={<SkeletonText />} />
          <Metadata
            label={"Application Interceptors"}
            text={<SkeletonText />}
          />
          <Metadata label={"Network Interceptors"} content={<SkeletonText />} />
          <Metadata label={"Send a Request"} content={<SkeletonText />} />
        </MetadataMenu>
      </Column>
    </FlexContainer>
  </Card>
)

const WebActionsContainer = (props: IState & IDispatchProps) => {
  const metadata = simpleSelect(props.webActions, "metadata")
  if (metadata.length > 0) {
    const filterKey =
      WebActionInternalLabel[
        simpleSelect(props.simpleForm, `${filterTag}::HTMLSelect`, "data") ||
          "All Metadata"
      ]
    const filterValue = simpleSelect(
      props.simpleForm,
      `${filterTag}::Input`,
      "data"
    )
    const filteredMetadata = chain(metadata)
      .filter((action: IWebActionInternal) =>
        ((action as any)[filterKey] || "")
          .toString()
          .toLowerCase()
          .includes(filterValue.toLowerCase())
      )
      .value()
    return (
      <div>
        <FilterWebActions {...props} />
        <WebActions
          metadata={filteredMetadata as IWebActionInternal[]}
          {...props}
        />
      </div>
    )
  } else {
    // Displays mock of 5 Web Action cards which fill in when data is available
    return (
      <div>
        <FilterWebActions {...props} />
        <SkeletonWebActions />
        <br />
        <SkeletonWebActions />
        <br />
        <SkeletonWebActions />
        <br />
        <SkeletonWebActions />
        <br />
        <SkeletonWebActions />
      </div>
    )
  }
}

const mapStateToProps = (state: IState) => rootSelectors(state)

export default connect(
  mapStateToProps,
  rootDispatcher
)(WebActionsContainer)
