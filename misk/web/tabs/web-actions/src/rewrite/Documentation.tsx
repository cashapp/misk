import React from "react";
import {Card, H5} from "@blueprintjs/core";

interface Props {
  documentation: String
}

export default function Documentation({documentation}: Props) {

  if (documentation) {
    return (
      <Card style={{marginTop: "12px", padding: "12px"}}>
        <H5> Documentation</H5>
        {documentation}
      </Card>
    )
  } else {
    return null;
  }

}
