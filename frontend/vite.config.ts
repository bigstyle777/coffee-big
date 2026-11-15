import { defineConfig, loadEnv } from "vite";
import uni from "@dcloudio/vite-plugin-uni";

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  // 读取 .env / .env.[mode] 中的配置，便于本地联调时切换后端地址
  const env = loadEnv(mode, process.cwd(), "");

  return {
    plugins: [uni()],
    server: {
      port: 5173,
      // H5 开发环境通过代理访问后端，避免浏览器跨域；小程序端请通过 VITE_API_BASE 指定绝对地址
      proxy: {
        "/api": {
          target: env.VITE_PROXY_TARGET || "http://localhost:8080",
          changeOrigin: true,
        },
      },
    },
  };
});
