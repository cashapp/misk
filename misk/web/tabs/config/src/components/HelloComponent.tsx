import * as React from 'react'
const Script = require('react-load-script')
import * as ReactDOM from 'react-dom'
import axios from 'axios'

interface HelloProps {
    compiler: string;
    framework: string;
}

const Hello = (props: HelloProps) => (
    <div>
      <h1>It's config y'allllllllllll bloop</h1>
      <h1>Hello from {props.compiler} and {props.framework}</h1>
    </div>
)

export class HelloComponent extends React.Component<HelloProps, {}> {
    public state = {
        components: {
            dashboard: ""
        }
    }

    render() {
        return (
            <div>
                <Hello {...this.props}/>
                {/* <script type="text/javascript">
                    {this.state.components.dashboard}
                </script>
                <script type="text/javascript">
                    console.log('static import');
                </script> */}
                {/* <Script url="/_admin/dashboard/tab_dashboard.js" /> */}
                <Script url="/_admin/test/import_test.js" />
            </div>
        )
    }
}