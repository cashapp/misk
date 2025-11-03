# Development Proxy Server

A simple proxy server written in TypeScript for testing applications against staging environments.

## Usage

```bash
# example: npm start https://staging-api.example.com
npm start <target-host>
```

The proxy server will start on `localhost:8080` and forward all requests to the specified target host.
