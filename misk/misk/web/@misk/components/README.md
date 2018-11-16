## Misk Components

![](https://raw.githubusercontent.com/square/misk/master/misk.png)
This package provides shared, styled React components across Misk tab repos. The top of each component/container file contains a usage example.

## Getting Started

```bash
$ yarn add @misk/components
```

## Builders

- `createApp(routes)`: builder function to create an `<App history={history}/>` component
- `createIndex(tabSlug, App, Ducks)`: builder function to create bootstrapping objects necessary for `index.tsx`

## Components

- `ErrorCalloutComponent`: Processes a Redux / Axios error and dumps raw JSON for debugging
- `OfflineComponent`: NonIdealState component for Offline or Loading tab state
- `PathDebugComponent`: outputs values passed in by props for `hash`, `pathname`, and `search` in React-Router instance
- `SidebarComponent`: dashboard styled sidebar
- `NavbarComponent`: dashboard styled Navbar

## Containers

- `DesktopWideOnlyContainer`: Only shows container when window width >1200px
- `FlexContainer`: Container using CSS FlexBox to have enclosed items flow responsively to screen width
- `MobileNeverContainer`: Never show container when window width <768px
- `MobileOnlyContainer`: Only show container when window width <768px
- `ResponsiveContainer`: Responsive container that all tabs and Nav Navbar use to ensure consistent view width

## [Releasing](https://github.com/square/misk/blob/master/misk/web/%40misk/RELEASING.md)

## [Changelog (and Breaking Changes)](https://github.com/square/misk/blob/master/misk/web/%40misk/CHANGELOG.md)
