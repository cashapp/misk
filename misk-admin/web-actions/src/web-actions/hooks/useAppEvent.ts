import { useEffect } from 'react';
import {
  appEvents,
  APP_EVENTS,
  AppEventType,
} from '@web-actions/events/appEvents';

type EventCallback<T = void> = (data: T) => void;

export function useAppEvent<T = void>(
  eventType: AppEventType,
  callback: EventCallback<T>,
) {
  useEffect(() => {
    return appEvents.on(eventType, callback);
  }, [eventType, callback]);
}
