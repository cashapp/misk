// Endpoint polling and auto-refresh script. This is only used with live reload enabled.
// When the liveness endpoint is not reachable, it will wait for the server to come back online and refresh the page.
(function() {
    // Configuration
    const POLL_ENDPOINT = '/_liveness'; // Change this to your backend endpoint
    const POLL_INTERVAL = 100;

    let isServerDown = false;
    let pollInterval;

    // Function to check if the server is alive
    async function checkServerStatus() {
        try {
            const response = await fetch(POLL_ENDPOINT, {
                method: 'GET',
                cache: 'no-cache',
                // Short timeout to quickly detect connection issues
                signal: AbortSignal.timeout(5000)
            });

            // If we get here, the server responded
            if (isServerDown) {
                // Server was down but is now back up
                console.log('Server is back online! Refreshing page...');
                clearInterval(pollInterval);
                setTimeout(() => {
                    window.location.reload();
                }, 500); // Small delay before refresh
            }

            // Reset retry count on successful connection
            retryCount = 0;

        } catch (error) {
            // Check if it's a connection error (server is down)
            if (error.name === 'TypeError' && error.message.includes('Failed to fetch')) {
                if (!isServerDown) {
                    console.log('Server connection lost. Waiting for it to come back...');
                    isServerDown = true;
                }
            }
        }
    }

    // Start polling
    console.log('Starting server status monitoring...');
    pollInterval = setInterval(checkServerStatus, POLL_INTERVAL);

    // Also check immediately
    checkServerStatus();
})();