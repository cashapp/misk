import * as React from 'react'
import * as ReactDOM from 'react-dom'
import axios from 'axios'

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
    public state = {
        components: {
            dashboard: ""
        }
    }

    componentDidMount() {
        axios.get('/_admin/test/import_test.js')
        .then(response => {
            const data = response.data
            const newState = {...this.state,
                components: {...this.state.components,
                    dashboard: data
                }
            }
            this.setState(newState)
            console.log(newState)
        })

        Object.entries(this.state.components).forEach(([key,value]) => {
            // TODO any way not to use eval?
            eval(value)
        })
    }

    render() {
        return (
            <div>
                <Hello {...this.props}/>
            </div>
        )
    }
}