import * as React from 'react'
const Script = require('react-load-script')

interface HelloProps {
    compiler: string;
    framework: string;
}

const Hello = (props: HelloProps) => (
    <div>
      <h1>It's dashboard y'allllllll strange</h1>
      <h1>Hello from {props.compiler} and {props.framework}</h1>
      <Script url="/_admin/config/tab_config.js" />
    </div>
)

export class HelloComponent extends React.Component<HelloProps, {}> {
    render() {
        return <Hello {...this.props}/>
    }
}