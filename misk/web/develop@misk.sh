#!/bin/sh
# Setup @misk/ packages for development forwarding from Misk service
server () {
  local port="${1:-8000}"
  sleep 1 && open "http://localhost:${port}/" &
  python -c $'import SimpleHTTPServer;\nmap = SimpleHTTPServer.SimpleHTTPRequestHandler.extensions_map;\nmap[""] = "text/plain";\nfor key, value in map.items():\n\tmap[key] = value + ";charset=UTF-8";\nSimpleHTTPServer.test();' "$port"
}

cd @misk/web
# If port is changed, it must be updated in Misk::AdminTabModule multibindings
server 9100

# Now a server runs at 9100 with root at ./@misk/web
# Any @misk/ package being worked on will have changes served after each `$ yarn build`