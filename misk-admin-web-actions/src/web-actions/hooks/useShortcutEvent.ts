import { useEffect } from 'react';
import {
  shortcutEvents,
  SHORTCUT_EVENTS,
  ShortcutEventType,
} from '@web-actions/events/shortcuts';

type EventCallback<T = void> = (data: T) => void;

export function useShortcutEvent<T = void>(
  eventType: ShortcutEventType,
  callback: EventCallback<T>,
) {
  useEffect(() => {
    return shortcutEvents.on(eventType, callback);
  }, [eventType, callback]);
}
