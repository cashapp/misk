import {
  ButtonGroup,
  Button,
  InputGroup,
  TextArea,
  H1,
  Intent,
  Pre
} from "@blueprintjs/core"
import { onClickFnCall, onChangeFnCall, simpleSelect } from "@misk/simpleredux"
import * as React from "react"
import { connect } from "react-redux"
import { IDispatchProps, IState, rootDispatcher, rootSelectors } from "../ducks"

export const SampleNetworkContainer = (props: IDispatchProps & IState) => {
  return (
    <div>
      <H1>Sample Network Component</H1>
      <Pre>
        sampleNetwork:
        {JSON.stringify(
          simpleSelect(props.simpleNetwork, "SampleNetwork"),
          null,
          2
        )}
      </Pre>
      <Pre>
        simpleForm:
        {JSON.stringify(
          simpleSelect(props.simpleForm, "SampleNetwork"),
          null,
          2
        )}
      </Pre>
      <Pre>
        url: {simpleSelect(props.simpleForm, "SampleNetwork::url", "data")}
      </Pre>
      <InputGroup
        placeholder={"Request URL: http://your.url.com/to/send/a/request/to/"}
        onChange={onChangeFnCall(props.simpleFormInput, "SampleNetwork::url")}
        type={"url"}
      />
      <TextArea
        fill={true}
        onChange={onChangeFnCall(props.simpleFormInput, "SampleNetwork::data")}
        placeholder={"Request Body (JSON or Text)"}
      />
      <ButtonGroup>
        <Button
          onClick={onClickFnCall(
            props.simpleNetworkGet,
            "SampleNetwork::DELETE",
            simpleSelect(props.simpleForm, "SampleNetwork::url", "data")
          )}
          intent={Intent.DANGER}
          loading={simpleSelect(
            props.simpleNetwork,
            "SampleNetwork::DELETE",
            "loading"
          )}
          text={"DELETE"}
        />
        <Button
          onClick={onClickFnCall(
            props.simpleNetworkGet,
            "SampleNetwork::GET",
            simpleSelect(props.simpleForm, "SampleNetwork::url", "data")
          )}
          intent={Intent.SUCCESS}
          loading={simpleSelect(
            props.simpleNetwork,
            "SampleNetwork::GET",
            "loading"
          )}
          text={"GET"}
        />
        <Button
          onClick={onClickFnCall(
            props.simpleNetworkHead,
            "SampleNetwork::HEAD",
            simpleSelect(props.simpleForm, "SampleNetwork::url", "data")
          )}
          intent={Intent.NONE}
          loading={simpleSelect(
            props.simpleNetwork,
            "SampleNetwork::HEAD",
            "loading"
          )}
          text={"HEAD"}
        />
        <Button
          onClick={onClickFnCall(
            props.simpleNetworkPatch,
            "SampleNetwork::PATCH",
            simpleSelect(props.simpleForm, "SampleNetwork::url", "data"),
            simpleSelect(props.simpleForm, "SampleNetwork::data", "data")
          )}
          intent={Intent.PRIMARY}
          loading={simpleSelect(
            props.simpleNetwork,
            "SampleNetwork::PATCH",
            "loading"
          )}
          text={"PATCH"}
        />
        <Button
          onClick={onClickFnCall(
            props.simpleNetworkPost,
            "SampleNetwork::POST",
            simpleSelect(props.simpleForm, "SampleNetwork::url", "data"),
            simpleSelect(props.simpleForm, "SampleNetwork::data", "data")
          )}
          intent={Intent.PRIMARY}
          loading={simpleSelect(
            props.simpleNetwork,
            "SampleNetwork::POST",
            "loading"
          )}
          text={"POST"}
        />
        <Button
          onClick={onClickFnCall(
            props.simpleNetworkPut,
            "SampleNetwork::PUT",
            simpleSelect(props.simpleForm, "SampleNetwork::url", "data"),
            simpleSelect(props.simpleForm, "SampleNetwork::data", "data")
          )}
          intent={Intent.WARNING}
          loading={simpleSelect(
            props.simpleNetwork,
            "SampleNetwork::PUT",
            "loading"
          )}
          text={"PUT"}
        />
      </ButtonGroup>
    </div>
  )
}

const mapStateToProps = (state: IState) => rootSelectors(state)

export default connect(
  mapStateToProps,
  rootDispatcher
)(SampleNetworkContainer)
