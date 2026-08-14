# 人物身高与素材挑选优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 人物增加可选身高（厘米）；人物编辑用分类+关键字+缩略图弹窗挑选素材；删人物时忽略/清理 DELETED 幽灵关联；人物管理页排版/交互首期美化。

**Architecture:** Flyway `V5` 增加 `height_cm`；扩展 Character DTO/Service 校验与透传；`CharacterService.delete` 仅对 NORMAL 关联 409，否则清 rel 后删人；`CharacterList.vue` 去掉文字多选，改为二级挑选弹窗复用 `listAssets` + `listCategories`，并整理筛选区/表格操作/弹窗两列布局。

**Tech Stack:** Java 17+、Spring Boot 3.3、JPA/Flyway、Vue 3、Element Plus

**Spec:** `docs/superpowers/specs/2026-08-14-character-height-asset-picker-design.md`

## Global Constraints

- 在 `master` 上直接提交
- 身高：`height_cm` / JSON `heightCm`，Integer 可空；有值须 ∈ [1, 300]
- 挑选：仅人物编辑；筛选分类+关键字；`status=NORMAL`；确定不自动保存关联
- UI 美化：仅人物管理页（筛选网格、操作更多菜单、编辑两列、挑选卡片）；不做整站换肤
- 删人物：仅 NORMAL 关联挡删除；DELETED 先删 `asset_character_rel`
- Git：`D:\tool\Git\bin\git.exe` + `-F` UTF-8 无 BOM；若注入 `--trailer` 用 `cmd /c`
- JDK：`JAVA_HOME=D:\jdk\jdk-24.0.1`；Node：`D:\tool\nvm\v22.17.0`
- TSD：改 `.java`/`.sql`/`.vue`/`.md` 后验明文；必要时 `.txt` + `cmd ren`
- 单测常用：`spring.flyway.enabled=false` + H2 `ddl-auto=create-drop`（实体字段即可；Flyway 给真实 MySQL）

---

## File Map

| Path | Responsibility |
|------|----------------|
| `.../db/migration/V5__character_height_cm.sql` | 加列 `height_cm` |
| `CharacterProfile.java` | 字段 `heightCm` |
| `CharacterCreateRequest.java` / `CharacterUpdateRequest.java` | 增加 `Integer heightCm`（record **末尾**） |
| `CharacterService.java` | 校验/写入身高；delete 幽灵关联 |
| `CharacterServiceTest.java` | 身高 + 删除用例；更新所有 `CharacterCreateRequest` 构造实参 |
| `story-admin-web/src/api/character.ts` | `heightCm` 类型 |
| `story-admin-web/src/views/characters/CharacterList.vue` | 身高 UI + 挑选弹窗 |
| Spec / README / 本计划验收表 | Task 5 文档 |

---

### Task 1: 人物身高（后端）

**Files:**
- Create: `story-admin-server/src/main/resources/db/migration/V5__character_height_cm.sql`
- Modify: `story-admin-server/src/main/java/com/story/admin/domain/CharacterProfile.java`
- Modify: `.../dto/CharacterCreateRequest.java`、`CharacterUpdateRequest.java`
- Modify: `.../service/CharacterService.java` — `applyFields` + create/update
- Modify: `.../service/CharacterServiceTest.java` — 全部 `new CharacterCreateRequest(...)` / `CharacterUpdateRequest(...)` 末尾补 `heightCm`；新增身高测试

**Interfaces:**
- Produces: create/update 接受 `heightCm`；实体 getter/setter `getHeightCm`/`setHeightCm`
- 校验：非 null 且 (值 < 1 或 > 300) → `400` `"heightCm must be between 1 and 300"`

- [x] **Step 1: 失败测试 — 非法身高**

在 `CharacterServiceTest` 增加：

```java
@Test
void createRejectsInvalidHeightCm() {
  assertThatThrownBy(
          () ->
              characterService.create(
                  new CharacterCreateRequest(
                      "高人", null, null, null, null, null, null, null, null, null, null, 0)))
      .isInstanceOf(ResponseStatusException.class)
      .hasMessageContaining("heightCm");
}
```

（先改 record 末尾加 `Integer heightCm`，否则编译不过；若先只加测试参数，允许与 Step 3 同批改 DTO。）

- [x] **Step 2: 失败测试 — 合法身高可落库**

```java
@Test
void createAndUpdateHeightCm() {
  CharacterProfile created =
      characterService.create(
          new CharacterCreateRequest(
              "测身高", null, null, null, null, null, null, null, null, null, null, 168));
  assertThat(created.getHeightCm()).isEqualTo(168);

  CharacterProfile updated =
      characterService.update(
          created.getId(),
          new CharacterUpdateRequest(
              "测身高", null, null, null, null, null, null, null, null, null, null, null));
  assertThat(updated.getHeightCm()).isNull();
}
```

- [x] **Step 3: Flyway + 实体 + DTO + Service**

`V5__character_height_cm.sql`（ASCII COMMENT，避免编码坑）：

```sql
ALTER TABLE character_profile
  ADD COLUMN height_cm INT NULL COMMENT 'height in centimeters' AFTER story_name;
```

`CharacterProfile`：

```java
@Column(name = "height_cm")
private Integer heightCm;
// getter/setter
```

DTO record **末尾**增加 `Integer heightCm`。

`applyFields` 增加参数 `Integer heightCm`，开头：

```java
if (heightCm != null && (heightCm < 1 || heightCm > 300)) {
  throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "heightCm must be between 1 and 300");
}
profile.setHeightCm(heightCm);
```

create/update 传入 `req.heightCm()`。同步改 `addForm` 里若调用 `applyFields`/`create` 的路径。

全仓库更新所有 `CharacterCreateRequest` / `CharacterUpdateRequest` 构造调用（末尾多一个实参）。

- [x] **Step 4: 跑测 PASS**

```bash
cd d:\study\mine\newWork\story-admin-server
set JAVA_HOME=D:\jdk\jdk-24.0.1
mvn -q -Dtest=CharacterServiceTest test
```

Expected: BUILD SUCCESS

- [x] **Step 5: Commit**

```bash
git commit -m "feat: add character heightCm in centimeters"
```

---

### Task 2: 删人物忽略 DELETED 关联（后端）

**Files:**
- Modify: `CharacterService.java` — `delete`
- Modify: `CharacterServiceTest.java`

**Interfaces:**
- Consumes: `characterRelRepository.findAssetIdsByCharacterId`、`assetRepository.findAllById`、`deleteByCharacterId`
- Produces: 仅 NORMAL → 409；否则清 rel + 删人

- [x] **Step 1: 失败测试（期望新行为 GREEN 前先写 RED）— 仅 DELETED 应可删**

```java
@Test
void deleteSucceedsWhenOnlyLinkedDeletedAssets() {
  Long assetId = persistAsset("ghost-link").getId();
  CharacterProfile character =
      characterService.create(
          new CharacterCreateRequest(
              "熊猫头测", null, null, null, null, null, null, null, null, null, null, null));
  assetService.update(
      assetId, AssetUpdateRequest.builder().characterIds(List.of(character.getId())).build());
  assetService.recycle(assetId);

  characterService.delete(character.getId());

  assertThat(characterProfileRepository.findById(character.getId())).isEmpty();
}
```

（沿用测试类已有 `persistAsset` / `assetService`；若无 recycle 助手则用现有 `AssetService.recycle`。）

- [x] **Step 2: 确认原测试 `deleteBlockedWhenLinkedToAsset` 仍 409**

保持用例不变（NORMAL 关联）。

- [x] **Step 3: 实现 delete**

```java
@Transactional
public void delete(Long id) {
  if (!repo.existsById(id)) {
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "character not found: " + id);
  }
  List<Long> assetIds = characterRelRepository.findAssetIdsByCharacterId(id);
  if (!assetIds.isEmpty()) {
    List<Long> normalIds =
        assetRepository.findAllById(assetIds).stream()
            .filter(a -> a.getStatus() == AssetStatus.NORMAL)
            .map(Asset::getId)
            .toList();
    if (!normalIds.isEmpty()) {
      throw new ConflictException(buildLinkedAssetSummary(normalIds));
    }
    characterRelRepository.deleteByCharacterId(id);
  }
  repo.deleteById(id);
}
```

确认 `AssetCharacterRelRepository` 已有 `deleteByCharacterId`；若无则加：

```java
void deleteByCharacterId(Long characterId);
```

- [x] **Step 4: 跑测 PASS + Commit**

```bash
mvn -q -Dtest=CharacterServiceTest test
git commit -m "fix: allow deleting character with only recycled asset links"
```

---

### Task 3: 人物身高 UI + 列表/编辑排版美化

**Files:**
- Modify: `story-admin-web/src/api/character.ts`
- Modify: `story-admin-web/src/views/characters/CharacterList.vue`

**Interfaces:**
- Consumes: API `heightCm`
- Produces: 表单可编辑；列表展示 `{n} cm` / `-`；筛选区/表格操作/编辑弹窗两列布局（Spec §6）

- [x] **Step 1: 类型**

`CharacterItem` / `CharacterPayload` / `CharacterFormNewCharacter` 增加 `heightCm: number | null`（create payload 允许 null）。

- [x] **Step 2: 身高字段接入**

- `form` / `resetForm` / `openEdit` / `payload()` 带上 `heightCm`
- 编辑表单项：`el-input-number`（可清空），label「身高 (cm)」
- `el-table-column`：有值 → `` `${row.heightCm} cm` ``，否则 `-`

- [x] **Step 3: 列表页排版**

- 筛选：CSS 网格/flex，统一 `el-form-item` 间距与控件最小宽度，避免一字换行；查询/重置成组
- 表格操作：保留「预览」「编辑」为 link/主操作；「添加形态」「删除」收入 `el-dropdown`「更多」
- 表头区保持简洁，不堆叠多余装饰

- [x] **Step 4: 编辑弹窗布局**

- `width` ≈ `840px`
- 基础字段两列：姓名|别名、所属故事|身高、性别|年龄/阶段、种族|身份/职业；公开简介/内部说明整行
- 素材区：分区标题 + 已关联缩略图固定边长、标题 `ellipsis`；按钮组「上传预览图」「从素材库指定」「保存关联」同行不挤

- [x] **Step 5: build + Commit**

```bash
cd d:\study\mine\newWork\story-admin-web
set Path=D:\tool\nvm\v22.17.0;%Path%
npm run build
git commit -m "feat: character height UI and list/editor layout polish"
```

Expected: `vue-tsc -b && vite build` PASS

---

### Task 4: 素材挑选弹窗 UI（含挑选区排版）

**Files:**
- Modify: `story-admin-web/src/views/characters/CharacterList.vue`
- Import: `listCategories` from `../../api/category`
- 使用现有 `listAssets`、`assetContentUrl`

**Interfaces:**
- Consumes: `listAssets({ status:'NORMAL', categoryId?, q? })`、`listCategories()`
- Produces: 二级弹窗多选；确定写回 `selectedLibraryIds`；仍点「保存关联」提交

- [x] **Step 1: 去掉文字 `el-select` library-pick**

删除「从素材库指定」的 `el-select` 块，改为打开挑选弹窗的按钮。

- [x] **Step 2: 挑选弹窗状态**

```ts
const pickerVisible = ref(false);
const pickerCategoryId = ref<number | 'all'>('all');
const pickerKeyword = ref('');
const pickerAssets = ref<AssetItem[]>([]);
const pickerSelectedIds = ref<number[]>([]);
const pickerLoading = ref(false);
const categories = ref<AssetCategoryItem[]>([]);
```

- [x] **Step 3: 打开/加载/确定 + 网格样式**

```ts
async function openAssetPicker() {
  pickerSelectedIds.value = [...selectedLibraryIds.value];
  pickerCategoryId.value = 'all';
  pickerKeyword.value = '';
  pickerVisible.value = true;
  if (!categories.value.length) {
    categories.value = await listCategories();
  }
  await loadPickerAssets();
}

async function loadPickerAssets() {
  pickerLoading.value = true;
  try {
    pickerAssets.value = await listAssets({
      status: 'NORMAL',
      categoryId: pickerCategoryId.value === 'all' ? undefined : pickerCategoryId.value,
      q: pickerKeyword.value.trim() || undefined,
    });
  } finally {
    pickerLoading.value = false;
  }
}

function confirmAssetPicker() {
  selectedLibraryIds.value = [...pickerSelectedIds.value];
  pickerVisible.value = false;
}
```

- 顶栏：分类 + 关键字同一行，间距固定  
- 网格：固定卡片宽高；选中描边/高亮；标题单行省略  
- 底栏：已选数量 + 取消/确定  

- [x] **Step 4: build + Commit**

```bash
npm run build
git commit -m "feat: character asset picker dialog with category keyword thumbs"
```

---

### Task 5: 验收与文档

**Files:**
- Modify: `docs/superpowers/specs/2026-08-14-character-height-asset-picker-design.md` — 状态 → 已实现（首期）
- Modify: `README.md` — 一句：人物身高 + 挑选弹窗 + 人物页排版首期
- Modify: 本计划文末追加验收表；勾选 Tasks

- [x] **Step 1: 后端验收**

```bash
mvn -q -Dtest=CharacterServiceTest test
```

对照 Spec §7：身高合法/非法；删仅 DELETED；NORMAL 仍 409。

- [x] **Step 2: 前端**

`npm run build` PASS；浏览器点验标 PARTIAL 可接受（分类/关键字/缩略图/保存关联；筛选与弹窗无明显一字换行）。

- [x] **Step 3: 文档 + Commit**

```bash
git commit -m "docs: record character height and asset picker acceptance"
```

---

## Spec Coverage

| Spec | Task |
|------|------|
| height_cm / heightCm / 1–300 | Task 1, 3 |
| 挑选弹窗分类+关键字+缩略图 | Task 4 |
| 确定不自动保存关联 | Task 4 |
| 删人物 DELETED 幽灵 | Task 2 |
| 人物页排版美化 §6 | Task 3, 4 |
| 验收文档 | Task 5 |

## Notes

- `CharacterCreateRequest` 末尾加字段后，**所有** Java 构造调用必须补参（含 Identity 相关测试）
- 重启后端后 Flyway V5 才会打到 MySQL；前端 Vite 热更新即可
- 勿提交 `pic/`；`storage/` 变更另议
- 其它管理页美化不在本计划；完成后可另开「工作台美化」计划

---

## Task 5 验收表（Spec §7）

| # | Criterion | Result | Evidence |
|---|-----------|--------|----------|
| 1 | 可保存/展示人物身高（含空）；非法值拒绝 | **PASS** | CharacterServiceTest#createAndUpdateHeightCm / #createRejectsInvalidHeightCm；UI 表单+列表列（Task 3） |
| 2 | 分类+关键字筛选+缩略图多选；确定后「保存关联」写库 | **PASS** (UI browser **PARTIAL**) | Task 4 picker 接线；
pm run build PASS；浏览器点验未跑 |
| 3 | 仅幽灵 DELETED 关联可删人物 | **PASS** | CharacterServiceTest#deleteSucceedsWhenOnlyLinkedDeletedAssets |
| 4 | NORMAL 关联删除仍 409 | **PASS** | CharacterServiceTest#deleteBlockedWhenLinkedToAsset |
| 5 | 人物页筛选/表格/编辑/挑选弹窗无明显一字换行 | **PARTIAL OK** | Task 3/4 布局已落地；浏览器点验未跑 |

| Extra | Result | Evidence |
|-------|--------|----------|
| Backend re-test (Task 5) | **PASS** | mvn -Dtest=CharacterServiceTest test EXIT=0；Tests run: 9, Failures: 0 (JDK 24.0.1) |
| Frontend build | **PASS** | Task 3/4 
pm run build |
| UI browser | **PARTIAL OK** | 按 brief 可接受 |

**Tasks 1–5 steps:** 全部勾选完成。
