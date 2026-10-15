/**
 * 轻量事件总线:用于跨页面广播"数据已变更"等通知
 * 用法:
 *   bus.emit('project-table:changed');
 *   bus.on('project-table:changed', () => { ... });
 */
type Handler = (...args: any[]) => void;

class TinyBus {
  private listeners = new Map<string, Set<Handler>>();

  on(event: string, fn: Handler) {
    if (!this.listeners.has(event)) this.listeners.set(event, new Set());
    this.listeners.get(event)!.add(fn);
    return () => this.off(event, fn);
  }

  off(event: string, fn: Handler) {
    this.listeners.get(event)?.delete(fn);
  }

  emit(event: string, ...args: any[]) {
    this.listeners.get(event)?.forEach(fn => {
      try { fn(...args); } catch (e) { /* swallow */ }
    });
  }
}

export const bus = new TinyBus();

/** 项目表数据变更事件 (导入 / 删除 / 编辑后触发) */
export const EVT_PROJECT_TABLE_CHANGED = 'project-table:changed';
