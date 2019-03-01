import { H1 } from "@blueprintjs/core"
import * as React from "react"
export const HowToComponent = () => {
  return (
    <div>
      <H1>Redux Powered Containers</H1>
      <p>
        There are examples below of forms, networks, buttons, tables, and other
        UI you will find helpful in building your Misk-Web tab. If you're
        looking for a way to test network functionality, HttpBin.org has a
        helpful echo Docker container that will do the trick.
      </p>
      <ol>
        <li>
          <code>$ docker run -p 1080:80 kennethreitz/httpbin</code>
        </li>
        <li>
          Use <code>http://localhost:1080/anything</code> as your URL for any
          URL requests below
        </li>
      </ol>
    </div>
  )
}

export default HowToComponent
