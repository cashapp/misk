Misk Components
---
![](https://raw.githubusercontent.com/square/misk/master/misk.png)
This package provides shared, styled React components across Misk tab repos. The top of each component/container file contains a usage example.

Getting Started
---
```bash
$ yarn add @misk/components
```

Components
---
- `ErrorCalloutComponent`: Processes a Redux / Axios error and dumps raw JSON for debugging
- `OfflineComponent`: NonIdealState component for Offline or Loading tab state
- `PathDebugComponent`: outputs values passed in by props for `hash`, `pathname`, and `search` in React-Router instance
- `SidebarComponent`: dashboard styled sidebar
- `TopbarComponent`: dashboard styled topbar

Containers
---
- `ResponsiveContainer`: Responsive container that all tabs and Nav Topbar use to ensure consistent view width

[Releasing](https://github.com/square/misk/blob/master/misk/web/%40misk/RELEASING.md)
---