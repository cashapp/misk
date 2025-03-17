import express from 'express';
import axios, { AxiosError } from 'axios';
import { URL } from 'url';

const showHelp = () => {
  console.log(`
Development Proxy Server
-----------------------

A proxy server for testing applications against different environments.

Usage:
  npm start <target-host>

Example:
  npm start https://staging-api.example.com

  This will start a proxy server on localhost:8080 that forwards all requests
  to https://staging-api.example.com while preserving headers, methods, and body content.

Options:
  --help, -h    Show this help message

Features:
  - Forwards all HTTP methods (GET, POST, PUT, DELETE, etc.)
  - Preserves request headers and body
  - Handles binary responses
  - Includes CORS headers for browser compatibility
  - Maintains original status codes
`);
  process.exit(1);
};

// Check for help flag
if (process.argv.includes('--help') || process.argv.includes('-h')) {
  showHelp();
}

const app = express();
const PORT = 8080;

// Parse raw body
app.use(express.raw({ type: '*/*' }));

// Main proxy handler for all routes
app.all('*', async (req, res) => {
  try {
    const host = process.argv[2];
    if (!host) {
      console.error('\nError: No target host provided!\n');
      showHelp();
      return;
    }

    // Basic validation of the host URL
    try {
      new URL(host);
    } catch (e) {
      console.error(`\nError: Invalid host URL provided: ${host}`);
      console.error('Please provide a valid URL including the protocol (http:// or https://)\n');
      showHelp();
      return;
    }

    const proxyTo = new URL(req.url, host).toString();
    console.log(`proxy to ${proxyTo}`);

     const response = await axios({
      method: req.method,
      url: proxyTo,
      data: req.method !== 'GET' ? req.body : undefined,
      headers: {
        'Content-Type': req.headers['content-type'],
        'Host': new URL(host).host,
      },
      responseType: 'arraybuffer',
      validateStatus: () => true, // Allow any status code
    });

    res.header('Access-Control-Allow-Origin', '*');
    res.header('Access-Control-Allow-Credentials', 'true');
    res.header(
      'Access-Control-Allow-Headers',
      'Access-Control-Allow-Headers, Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers'
    );

    // Forward the response
    if (response.headers['content-type']) {
      res.header('Content-Type', response.headers['content-type']);
    }
    
    res.status(response.status).send(response.data);

  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : 'Unknown error occurred';
    console.error('Proxy Error:', errorMessage);
    res.status(500).send('Proxy Error: ' + errorMessage);
  }
});

const host = process.argv[2];
if (!host) {
  console.error('\nError: No target host provided!\n');
  showHelp();
} else {
  app.listen(PORT, () => {
    console.log(`
🌎 Development Proxy Server Started
---------------------------------
From: http://localhost:${PORT}
To:   ${process.argv[2]}

Press Ctrl+C to stop the server
`);
  });
}
