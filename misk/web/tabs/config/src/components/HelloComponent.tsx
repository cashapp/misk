import * as React from 'react'

interface HelloProps {
    compiler: string;
    framework: string;
}

const Hello = (props: HelloProps) => (
    <div>
      <h1>It's config y'allllllllllllllll</h1>
      <h1>Hello from {props.compiler} and {props.framework}</h1>
    </div>
)

export class HelloComponent extends React.Component<HelloProps, {}> {
    render() {
        return <Hello {...this.props}/>
    }
}