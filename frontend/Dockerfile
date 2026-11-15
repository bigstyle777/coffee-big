# 构建 H5 产物
FROM node:20-alpine AS builder
WORKDIR /app

# 小程序构建时可通过 --build-arg VITE_API_BASE=https://api.example.com/api 注入
ARG VITE_API_BASE=""
ENV VITE_API_BASE=${VITE_API_BASE}

COPY package.json package-lock.json ./
RUN npm ci --registry=https://registry.npmmirror.com

COPY . .
RUN npm run build:h5

# 静态资源 + 反向代理
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist/build/h5 /usr/share/nginx/html

EXPOSE 80
