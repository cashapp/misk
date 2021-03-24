import React, { useState } from "react"
import {
  Button,
  FormGroup,
  InputGroup,
  H5,
  HTMLSelect,
  NumericInput
} from "@blueprintjs/core"
import _ from "lodash"
import { WebActionMetadata, ProtoField } from "./types"
import axios from "axios"
import { AppToaster } from "./Toaster"

interface Props {
  webActionMetadata: WebActionMetadata
}

export default function WebActionSendRequest({ webActionMetadata }: Props) {
  const [request, setRequest] = useState<any>({})
  const [loading, setLoading] = useState(false)
  const [response, setResponse] = useState("")
  const [url, setUrl] = useState(webActionMetadata.pathPattern)
  const requestType = webActionMetadata.types[webActionMetadata.requestType]

  const handleSubmit = () => {
    setLoading(true)
    let axiosRequest

    if (webActionMetadata.httpMethod === "POST") {
      axiosRequest = axios.post(url, request)
    } else if (webActionMetadata.httpMethod === "GET") {
      axiosRequest = axios.get(url)
    } else {
      AppToaster.show({
        message: `Unsupported request type: ${webActionMetadata.httpMethod}`,
        intent: "danger"
      })
      return
    }

    axiosRequest
      .then(response => {
        setResponse(JSON.stringify(response.data, null, 2))
      })
      .catch(e => {
        setResponse(e.message)
      })
      .finally(() => {
        setLoading(false)
      })
  }

  const handleOnChange = (path: string[], value: any) => {
    setRequest((currentValue: any) => {
      const copyOfState = _.clone(currentValue)
      _.set(copyOfState, path, value)
      return copyOfState
    })
  }

  const toFormElement = (field: ProtoField, path: string[]) => {
    if (field.repeated) {
      return (
        <p style={{ color: "red" }}>
          Repeated fields aren't supported currently.
        </p>
      )
    }

    if (field.type === "String" || field.type === "ByteString") {
      return (
        <FormGroup label={`${field.name} (${field.type})`}>
          <InputGroup
            placeholder={field.type}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              handleOnChange(path.concat(field.name), e.currentTarget.value)
            }
          />
        </FormGroup>
      )
    }

    if (field.type === "Int" || field.type === "Long") {
      return (
        <FormGroup label={`${field.name} (${field.type})`}>
          <NumericInput
            buttonPosition="none"
            placeholder={field.type}
            onValueChange={(e: number) =>
              handleOnChange(path.concat(field.name), e)
            }
          />
        </FormGroup>
      )
    }

    if (field.type === "Boolean") {
      return (
        <FormGroup label={`${field.name} (${field.type})`}>
          <HTMLSelect
            options={["", "True", "False"]}
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
              handleOnChange(
                path.concat(field.name),
                e.currentTarget.value === "True"
              )
            }
          />
        </FormGroup>
      )
    }

    if (field.type.startsWith("Enum")) {
      // Looks like Enum<com.squareup.protos.cash.loanstar.data.AutoPay,ENABLED,DISABLED>.
      const enumValues = field.type
        .replace(">", "")
        .substring(5)
        .split(",")
      const protoName = enumValues.shift()
      enumValues.unshift("") // Add a blank value to use to not include in the request.
      return (
        <FormGroup label={`${field.name} (${protoName})`}>
          <HTMLSelect
            options={enumValues}
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
              handleOnChange(path.concat(field.name), e.currentTarget.value)
            }
          />
        </FormGroup>
      )
    }

    const complexType = webActionMetadata.types[field.type]
    if (!complexType) {
      return (
        <p style={{ color: "red" }}>Unsupported field type {field.type}.</p>
      )
    }
    return (
      <FormGroup label={`${field.name} (${field.type})`}>
        <div style={{ marginLeft: "8px" }}>
          {complexType.fields.map(subField =>
            toFormElement(subField, path.concat(field.name))
          )}
        </div>
      </FormGroup>
    )
  }

  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        columnGap: "16px"
      }}
    >
      <div>
        <H5>Request</H5>
        {requestType ? (
          requestType.fields.map(field => toFormElement(field, []))
        ) : (
          <p>No request fields found.</p>
        )}
      </div>
      <div>
        {webActionMetadata.pathPattern.includes("{") ? (
          <FormGroup label="Request Path">
            <InputGroup
              defaultValue={webActionMetadata.pathPattern}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                setUrl(e.currentTarget.value)
              }
            />
          </FormGroup>
        ) : (
          <></>
        )}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between"
          }}
        >
          <H5 style={{ margin: 0 }}>Request Body</H5>
          <div>
            <Button
              intent="success"
              small={true}
              minimal={true}
              style={{ margin: 0 }}
              onClick={() => setRequest({})}
            >
              Clear Request
            </Button>
            <Button
              intent="success"
              small={true}
              minimal={true}
              style={{ margin: 0 }}
              onClick={handleSubmit}
              loading={loading}
            >
              Send Request
            </Button>
          </div>
        </div>
        <pre>{JSON.stringify(request, null, 2)}</pre>

        <H5>Response</H5>
        <pre>{response}</pre>
      </div>
    </div>
  )
}
