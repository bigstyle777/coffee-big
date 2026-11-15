/**
 * 全局配置
 *
 * - 微信小程序 / 支付宝小程序 / App：直连后端，局域网真机调试时请把 localhost 换成本机 IP
 * - H5 开发环境：走 vite.config.ts 中的 /api 代理，规避浏览器跨域
 * - H5 生产部署：需要将 /api 反向代理到后端服务
 * - 所有端均可通过环境变量 VITE_API_BASE 覆盖
 */
const envBase = import.meta.env.VITE_API_BASE as string | undefined

/** 默认后端地址：H5 用同源 /api，其余端用绝对地址 */
let defaultBase = 'http://localhost:8080/api'

// #ifdef H5
defaultBase = '/api'
// #endif

/** 后端接口基础路径（不含结尾斜杠） */
export const API_BASE_URL = envBase || defaultBase

/** 请求超时时间（毫秒） */
export const REQUEST_TIMEOUT = 15000
