# 插图 + 文字故事阅读系统

本仓库采用四项目单仓库结构：

- `story-user-server`：使用端 Java 后端
- `story-admin-server`：管理端 Java 后端
- `story-user-web`：使用端 Vue 前端
- `story-admin-web`：管理端 Vue 前端

## 技术栈

- 后端：Java 17、Spring Boot 3.3、Maven
- 前端：Vue 3、Vue Router、Vite、TypeScript

## 本地启动

要求：JDK 17+、Maven 3.9+、Node.js 20+、npm 10+。

分别启动两个后端：

```bash
cd story-user-server
mvn spring-boot:run

cd story-admin-server
mvn spring-boot:run
```

使用端后端运行于 `http://localhost:8080`，管理端后端运行于 `http://localhost:8081`。

分别启动两个前端：

```bash
cd story-user-web
npm install
npm run dev

cd story-admin-web
npm install
npm run dev
```

使用端前端运行于 `http://localhost:5173`，管理端前端运行于 `http://localhost:5174`。开发服务器已分别代理 `/api` 到对应后端。

## 当前基础能力

两个后端均提供 `/api/health` 健康检查；两个前端均包含首页、路由与后端连通状态展示，方便后续逐步加入故事、分类、标签、素材及用户等业务模块。

