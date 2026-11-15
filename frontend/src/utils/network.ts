/**
 * 网络状态：小程序 / App / H5 通用的轻量封装
 *
 * 菜单页用它做「断网时展示缓存菜单、恢复网络自动刷新」。
 */
type NetworkListener = (online: boolean) => void

let online = true
let started = false
const listeners = new Set<NetworkListener>()

function emit(value: boolean): void {
  if (online === value) {
    return
  }
  online = value
  listeners.forEach((listener) => listener(value))
}

function start(): void {
  if (started) {
    return
  }
  started = true
  uni.getNetworkType({
    success: (res) => emit(res.networkType !== 'none'),
    // 拿不到网络类型时保持乐观的在线状态，交给请求失败兜底
    fail: () => undefined,
  })
  uni.onNetworkStatusChange((res) => emit(res.isConnected))
}

/** 当前是否在线（未知时按在线处理） */
export function isOnline(): boolean {
  return online
}

/**
 * 订阅网络状态，返回取消订阅函数
 * 订阅瞬间会立即回调一次当前状态，方便页面初始化
 */
export function watchNetwork(listener: NetworkListener): () => void {
  start()
  listeners.add(listener)
  listener(online)
  return () => {
    listeners.delete(listener)
  }
}
