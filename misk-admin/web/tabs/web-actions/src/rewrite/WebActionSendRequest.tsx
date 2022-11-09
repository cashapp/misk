import React, { useEffect, useState } from "react"
import _ from "lodash"
import { Button, FormGroup, H5, InputGroup } from "@blueprintjs/core"
import { WebActionMetadata } from "./types"
import { FormComponent } from "./WebActionSendRequestFormComponents"
import axios from "axios"
import { AppToaster } from "./Toaster"

interface Props {
  webActionMetadata: WebActionMetadata
}

export default function WebActionSendRequest({ webActionMetadata }: Props) {
  const [requestInput, setRequestInput] = useState<any>(null) // request view model
  const [requestRaw, setRequestRaw] = useState<any>({}) // request raw data
  const [loading, setLoading] = useState(false)
  const [response, setResponse] = useState("")
  const [url, setUrl] = useState(webActionMetadata.pathPattern)

  useEffect(() => {
    const requestInputAsString = JSON.stringify(
      requestInput,
      (key, val) => {
        // filter out nulls
        if (_.isNull(val)) return
        if (Array.isArray(val)) return _.reject(val, _.isNull)
        return val
      },
      2
    )
    if (_.isEmpty(requestInputAsString)) {
      setRequestRaw({})
    } else {
      setRequestRaw(JSON.parse(requestInputAsString))
    }
  }, [requestInput])

  const handleSubmit = () => {
    setLoading(true)
    let axiosRequest

    if (webActionMetadata.httpMethod === "POST") {
      axiosRequest = axios.post(url, requestRaw)
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
        if (e.response) {
          setResponse(
            JSON.stringify(
              _.pick(e.response, "data", "status", "statusText"),
              null,
              2
            )
          )
        } else {
          setResponse(e.message)
        }
      })
      .finally(() => {
        setLoading(false)
      })
  }

  const requestBodyForm = () => {
    return (
      <FormComponent
        webActionMetadata={webActionMetadata}
        field={{
          name: "request",
          type: webActionMetadata.requestType,
          repeated: false
        }}
        onChange={value => {
          setRequestInput(value)
        }}
        value={requestInput}
      />
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
        {webActionMetadata.types[webActionMetadata.requestType] ? (
          requestBodyForm()
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
            flexWrap: "wrap",
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
              onClick={() => setRequestInput({})}
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
        <pre style={{ whiteSpace: "pre-wrap" }}>
          {JSON.stringify(requestRaw, null, 2)}
        </pre>
        <H5>Response</H5>
        <pre style={{ whiteSpace: "pre-wrap" }}>{response}</pre>
      </div>
    </div>
  )
}
