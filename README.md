# 插图 + 文字故事阅读系统

## 项目文档

- [需求与设计基线](docs/PROJECT_REQUIREMENTS.md)：记录已确认需求、核心设计原则、首期范围和待确认事项。后续开发应以此文档为持续更新的项目上下文。
- [管理端素材模块设计](docs/superpowers/specs/2026-08-13-asset-module-design.md)：标准首期素材能力设计（状态：已实现）。

本仓库采用四项目单仓库结构：

- `story-user-server`：使用端 Java 后端
- `story-admin-server`：管理端 Java 后端
- `story-user-web`：使用端 Vue 前端
- `story-admin-web`：管理端 Vue 前端

## 技术栈

- 后端：Java 17、Spring Boot 3.3、Maven
- 前端：Vue 3、Vue Router、Vite、TypeScript、Element Plus

## 本地启动

要求：JDK 17+、Maven 3.9+、Node.js 20+、npm 10+、MySQL 8（管理端）。

### 管理端本地 Runbook（素材模块）

1. **数据库**：创建本机库 `story_admin`（账号示例 `root`）。
2. **本地配置**：复制示例文件并填写真实密码（该文件已 gitignore，勿提交）：

   ```bash
   cp story-admin-server/src/main/resources/application-local.yml.example \
      story-admin-server/src/main/resources/application-local.yml
   ```

   关键键：`spring.datasource.url/username/password`。默认 `spring.profiles.active=local`。
3. **文件存储**：仓库根目录 `storage/`（已 gitignore；库内仅存相对路径）。默认 `story.storage.root=../storage`。
4. **后端**（端口 **8081**）：

   ```bash
   cd story-admin-server
   mvn spring-boot:run
   ```

5. **前端**（端口 **5174**，`/api` 代理到 8081）：

   ```bash
   cd story-admin-web
   npm install
   npm run dev
   ```

6. 健康检查：`GET http://localhost:8081/api/health` → `{"status":"ok","service":"story-admin-server"}`。

### 使用端 / 双端一并启动

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

## 当前状态

素材模块（首期）：API 联调 smoke **PASS**；浏览器 UI 点验 **PARTIAL**（详见 `.superpowers/sdd/task-9-report.md` / 计划文末 §9 表）。

## 当前基础能力

- 使用端：`/api/health` 健康检查；前端首页与后端连通状态。
- 管理端（素材首期）：分类/素材上传与预览、拖拽排序与跨分类、标签与人物关联、回收站、AI 参考区（无真模型）、系统配置 CRUD + 默认回退、人物基础 CRUD。
