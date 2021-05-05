import React, { useContext } from "react"
import {ProtobufDocUrlPrefix} from "./TabContainer"

interface Props {
  protoClass: String
}

export default function LinkToProtoDoc({ protoClass }: Props) {
  const protobufDocUrlPrefix = useContext(ProtobufDocUrlPrefix)

  return (
    <>
      {protobufDocUrlPrefix ? (
        <a href={protobufDocUrlPrefix + protoClass}>{protoClass}</a>
      ) : null}
    </>
  )
}
