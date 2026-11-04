# 前端镜像：Vite 构建 → nginx 静态托管并反向代理 /api
# 构建上下文为仓库根：npm workspaces 需要根 package.json / package-lock.json 与 packages/scaffold-ui 的物料源码
FROM node:22-alpine AS build
WORKDIR /build
# 先只拷三份清单，依赖未变时 npm ci 这一层可复用缓存
COPY package.json package-lock.json ./
COPY packages/scaffold-ui/package.json packages/scaffold-ui/
COPY scaffold-vue/package.json scaffold-vue/
RUN npm ci
COPY packages packages
COPY scaffold-vue scaffold-vue
RUN npm --workspace scaffold-vue run build

FROM nginx:1.27-alpine
COPY scaffold-vue/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/scaffold-vue/dist /usr/share/nginx/html
EXPOSE 80
