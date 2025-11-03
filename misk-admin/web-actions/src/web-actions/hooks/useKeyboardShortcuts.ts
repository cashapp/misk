import { useEffect } from 'react';
import { appEvents, APP_EVENTS } from '@web-actions/events/appEvents';

export function useKeyboardShortcuts() {
  useEffect(() => {
    const handleKeyPress = (event: KeyboardEvent) => {
      const isShortcutKey = event.ctrlKey || event.metaKey;

      if (isShortcutKey && event.key === '/') {
        event.preventDefault();
        event.stopPropagation();
        appEvents.emit(APP_EVENTS.TOGGLE_HELP);
        return;
      }

      if (isShortcutKey && event.key === 'k') {
        event.preventDefault();
        appEvents.emit(APP_EVENTS.FOCUS_ENDPOINT_SELECTOR);
      } else if (isShortcutKey && event.key === 'Enter') {
        appEvents.emit(APP_EVENTS.SUBMIT_REQUEST);
      }
    };

    document.addEventListener('keydown', handleKeyPress, true);

    return () => {
      document.removeEventListener('keydown', handleKeyPress, true);
    };
  }, []);
}
