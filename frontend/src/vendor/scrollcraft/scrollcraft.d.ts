export interface ScrollCraftInstance {
  destroy(): void;
  layout(): void;
}
declare global {
  interface Window {
    ScrollCraft?: { mount(root: Element | string, opts?: Record<string, unknown>): ScrollCraftInstance; reduce: boolean };
  }
}
