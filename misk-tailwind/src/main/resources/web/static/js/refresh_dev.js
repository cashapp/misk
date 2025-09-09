// Endpoint long polling and auto-refresh script. This is only used with live reload enabled.
// When the check-reload endpoint returns an updated reload marker, it will refresh the page.
// If the server is down, it will keep retrying until the server comes back up.
(function() {
    // Configuration
    const POLL_ENDPOINT = '/_dev/check-reload';

    let isServerDown = false;
    let reloadMarker = null;

    // Function to check if the server is alive
    async function checkServerStatus() {
        try {
            let response = await fetch(POLL_ENDPOINT, {
                method: 'GET',
                headers : {
                    'If-None-Match': reloadMarker
                }
            });

            let nextReloadMarker = response.headers.get('Etag');
            if (reloadMarker == null) {
                reloadMarker = nextReloadMarker;
            } else if (reloadMarker !== nextReloadMarker && nextReloadMarker != null) {
                console.log('Server has restarted. Reloading page... ' + reloadMarker + ' -> ' + nextReloadMarker);
                window.location.reload();
            } else if (isServerDown) {
                console.log('Server connection restored.');
                isServerDown = false;
            }
            checkServerStatus();
        } catch (error) {
            if (!isServerDown) {
                console.log('Server connection lost. Waiting for it to come back...');
                isServerDown = true;
            }
            setTimeout(checkServerStatus, 1000);
        }
    }

    console.log('Starting server status monitoring...');
    checkServerStatus();
})();