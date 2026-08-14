# 插图 + 文字故事阅读系统

## 项目文档

- [需求与设计基线](docs/PROJECT_REQUIREMENTS.md)：记录已确认需求、核心设计原则、首期范围和待确认事项。后续开发应以此文档为持续更新的项目上下文。
- [管理端素材模块设计](docs/superpowers/specs/2026-08-13-asset-module-design.md)：标准首期素材能力设计（状态：已实现）。
- [管理端素材组合编排设计](docs/superpowers/specs/2026-08-13-asset-combo-design.md)：组合成员/播放序列/步进停留与预览（状态：已实现（首期））。
- [管理端人物本体与多形态设计](docs/superpowers/specs/2026-08-13-character-identity-design.md)：人物本体、形态映射、共用素材与「添加形态」（状态：已实现（首期））。
- [管理端素材工作台筛选与替换设计](docs/superpowers/specs/2026-08-13-asset-workbench-filter-replace-design.md)：工作台默认「无关联」人物筛选 + 单张替换图片（状态：已实现（首期））。
- [人物身高与素材挑选优化](docs/superpowers/specs/2026-08-14-character-height-asset-picker-design.md)：人物可选身高（cm）+ 分类/关键字缩略图挑选弹窗 + 人物管理页排版首期美化（状态：已实现（首期））。

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
3. **文件存储**：仓库根目录 `storage/`（**纳入 Git**；数据库仅存相对路径与元数据）。默认 `story.storage.root=../storage`。管理端上传/移入回收站/硬删后，请同步提交 `storage/` 变更。
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
7. **组合编排入口**：侧栏「素材管理 → 组合编排」，路由 `/assets/combos`（编辑 `/assets/combos/:id`）；API 前缀 `/api/combos`。
8. **人物本体入口**：侧栏「人物管理 → 人物本体」，路由 `/character-identities`（编辑 `/character-identities/:id`）；人物列表 `/characters` 含所属本体列与「添加形态」；API 前缀 `/api/character-identities`，升级流 `POST /api/characters/{id}/forms`。
9. **素材工作台筛选与替换**：路由 `/assets`；默认人物筛选「无关联」（`characterFilter=unlinked`）；详情「替换图片」覆盖原文件（`POST /api/assets/{id}/content`），素材 id / 分类 / 关联不变。

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

组合编排（首期）：API + 单测 smoke **PASS**；浏览器预览/循环点验 **PARTIAL**（详见 `.superpowers/sdd/task-7-report.md` / `docs/superpowers/plans/2026-08-13-asset-combo.md` 文末 §9 表）。

人物本体（首期）：API + 单测 smoke **PASS**；浏览器列表/添加形态点验 **PARTIAL**（详见 `.superpowers/sdd/task-6-report.md` / `docs/superpowers/plans/2026-08-13-character-identity.md` 文末 §7 表）。

## 当前基础能力

- 使用端：`/api/health` 健康检查；前端首页与后端连通状态。
- 管理端（素材首期）：分类/素材上传与预览、拖拽排序与跨分类、标签与人物关联、回收站、AI 参考区（无真模型）、系统配置 CRUD + 默认回退、人物基础 CRUD。
- 管理端（组合编排首期）：组合 CRUD、成员 1..n、播放序列/默认间隔/步进停留、循环预览、素材硬删组合引用拦截（409）。
- 管理端（人物本体首期）：本体 CRUD、形态 members 全量设置、本体共用素材、人物 `identityId`/`formLabel`、添加形态自动建本体、删本体/硬删素材引用拦截（409）。
