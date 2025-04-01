export type AppEventType =
  | 'focus-endpoint-selector'
  | 'toggle-help'
  | 'submit-request'
  | 'endpoint-selected'
  | 'show-error-toast';

export const APP_EVENTS = {
  FOCUS_ENDPOINT_SELECTOR: 'focus-endpoint-selector' as const,
  TOGGLE_HELP: 'toggle-help' as const,
  SUBMIT_REQUEST: 'submit-request' as const,
  ENDPOINT_SELECTED: 'endpoint-selected' as const,
  SHOW_ERROR_TOAST: 'show-error-toast' as const,
};

class AppEventBus {
  private readonly eventTarget: EventTarget;
  private count: number;

  constructor() {
    this.eventTarget = new EventTarget();
    this.count = 0;
  }

  emit(eventType: AppEventType, data?: any) {
    this.eventTarget.dispatchEvent(
      new CustomEvent(eventType, { detail: data }),
    );
  }

  on(eventType: AppEventType, callback: (data?: any) => void) {
    const wrappedCallback = (event: Event) => {
      callback((event as CustomEvent).detail);
    };
    this.count += 1;
    this.eventTarget.addEventListener(eventType, wrappedCallback);
    return () => this.off(eventType, wrappedCallback);
  }

  off(eventType: AppEventType, callback: (data?: any) => void) {
    this.count -= 1;
    this.eventTarget.removeEventListener(eventType, callback);
  }
}

export const appEvents = new AppEventBus();
