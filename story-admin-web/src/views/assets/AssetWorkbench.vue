<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import draggable from 'vuedraggable';
import {
  assetContentUrl,
  listAssets,
  moveAsset,
  recycleAsset,
  reorderAssets,
  replaceAssetContent,
  updateAsset,
  uploadAssets,
  type AssetItem,
} from '../../api/asset';
import { useRouter } from 'vue-router';
import { listCategories, type AssetCategoryItem } from '../../api/category';
import { listCharacters, type CharacterItem } from '../../api/character';

const router = useRouter();
const loading = ref(false);
const uploading = ref(false);
const saving = ref(false);
const recycling = ref(false);
const replacing = ref(false);
const previewBust = ref(0);
const replaceFileInput = ref<HTMLInputElement | null>(null);
const categories = ref<AssetCategoryItem[]>([]);
const assets = ref<AssetItem[]>([]);
const selectedCategoryId = ref<number | null>(null);
const selectedAssetId = ref<number | null>(null);
const search = ref('');
/** 无关联 | 具体人物 id | 全部 */
const characterFilter = ref<'unlinked' | 'all' | number>('unlinked');
const indexInput = ref('1');
const fileInput = ref<HTMLInputElement | null>(null);
const categoryBuckets = reactive<Record<number, AssetItem[]>>({});
const dragging = ref(false);
const thumbGroup = { name: 'assets', pull: true, put: false };
const categoryGroup = { name: 'assets', pull: false, put: true };

type DragChangeEvent = {
  added?: { element: AssetItem; newIndex: number };
  removed?: { element: AssetItem; oldIndex: number };
  moved?: { element: AssetItem; oldIndex: number; newIndex: number };
};

let dragSnapshot: AssetItem[] = [];
let dragEndedAt = 0;

function bucketFor(categoryId: number): AssetItem[] {
  if (!categoryBuckets[categoryId]) {
    categoryBuckets[categoryId] = [];
  }
  return categoryBuckets[categoryId];
}

function clearBuckets() {
  for (const key of Object.keys(categoryBuckets)) {
    const bucket = categoryBuckets[Number(key)];
    bucket.splice(0, bucket.length);
  }
}

function restoreDragSnapshot() {
  assets.value = dragSnapshot.map((item) => ({ ...item }));
  clearBuckets();
}

function onDragStart() {
  dragging.value = true;
  dragSnapshot = assets.value.map((item) => ({ ...item }));
}

function onDragEnd() {
  dragging.value = false;
  dragEndedAt = Date.now();
}

async function onThumbsChange(evt: DragChangeEvent) {
  if (!evt.moved || selectedCategoryId.value == null) return;
  if (isSearchActive.value) {
    restoreDragSnapshot();
    return;
  }
  try {
    await reorderAssets({
      categoryId: selectedCategoryId.value,
      orderedIds: assets.value.map((item) => item.id),
    });
  } catch (e) {
    restoreDragSnapshot();
    ElMessage.error(apiError(e, '排序失败'));
  }
}

async function onDropOnCategory(categoryId: number, evt: DragChangeEvent) {
  if (!evt.added) return;
  const item = evt.added.element;
  const bucket = bucketFor(categoryId);
  bucket.splice(0, bucket.length);
  if (categoryId === selectedCategoryId.value) {
    restoreDragSnapshot();
    return;
  }
  try {
    await moveAsset(item.id, {
      targetCategoryId: categoryId,
      targetIndex: evt.added.newIndex ?? 0,
    });
    ElMessage.success('已移动到目标分类');
    const keepId = selectedAssetId.value === item.id ? null : selectedAssetId.value;
    await loadAssets(keepId);
  } catch (e) {
    restoreDragSnapshot();
    ElMessage.error(apiError(e, '移动失败'));
  }
}

const characters = ref<CharacterItem[]>([]);

const form = reactive({
  displayName: '',
  description: '',
  chapterRefPlaceholder: '',
  tagNames: [] as string[],
  characterIds: [] as number[],
});

const selectedCategory = computed(
  () => categories.value.find((c) => c.id === selectedCategoryId.value) ?? null,
);
const currentIndex = computed(() =>
  assets.value.findIndex((a) => a.id === selectedAssetId.value),
);
const currentAsset = computed(() =>
  currentIndex.value >= 0 ? assets.value[currentIndex.value] : null,
);
const canPrev = computed(() => currentIndex.value > 0);
const canNext = computed(
  () => currentIndex.value >= 0 && currentIndex.value < assets.value.length - 1,
);
/** q 非空或人物不是「全部」时禁用本分类内排序 */
const isSearchActive = computed(
  () => search.value.trim().length > 0 || characterFilter.value !== 'all',
);

function apiError(e: unknown, fallback: string): string {
  const err = e as { response?: { data?: { message?: string } } };
  return err?.response?.data?.message || fallback;
}

async function loadCategories(preferId?: number | null) {
  categories.value = await listCategories();
  for (const cat of categories.value) {
    bucketFor(cat.id);
  }
  const keep =
    preferId != null && categories.value.some((c) => c.id === preferId)
      ? preferId
      : selectedCategoryId.value != null &&
          categories.value.some((c) => c.id === selectedCategoryId.value)
        ? selectedCategoryId.value
        : (categories.value[0]?.id ?? null);
  selectedCategoryId.value = keep;
}

async function loadAssets(keepId?: number | null) {
  if (selectedCategoryId.value == null) {
    assets.value = [];
    selectedAssetId.value = null;
    return;
  }
  loading.value = true;
  try {
    const listParams: Parameters<typeof listAssets>[0] = {
      categoryId: selectedCategoryId.value,
      status: 'NORMAL',
      q: search.value.trim() || undefined,
    };
    if (typeof characterFilter.value === 'number') {
      listParams.characterId = characterFilter.value;
    } else {
      listParams.characterFilter = characterFilter.value;
    }
    assets.value = await listAssets(listParams);
    const preferred = keepId ?? selectedAssetId.value;
    if (preferred != null && assets.value.some((a) => a.id === preferred)) {
      selectedAssetId.value = preferred;
    } else {
      selectedAssetId.value = assets.value[0]?.id ?? null;
    }
  } catch (e) {
    ElMessage.error(apiError(e, '加载素材失败'));
  } finally {
    loading.value = false;
  }
}

function syncForm() {
  const asset = currentAsset.value;
  form.displayName = asset?.displayName ?? '';
  form.description = asset?.description ?? '';
  form.chapterRefPlaceholder = asset?.chapterRefPlaceholder ?? '';
  form.tagNames = [...(asset?.tagNames ?? [])];
  form.characterIds = [...(asset?.characterIds ?? [])];
  indexInput.value = currentIndex.value >= 0 ? String(currentIndex.value + 1) : '';
}

watch(currentAsset, syncForm, { immediate: true });

watch(selectedAssetId, async () => {
  await nextTick();
  const el = document.querySelector<HTMLElement>(`[data-thumb-id="${selectedAssetId.value}"]`);
  el?.scrollIntoView({ inline: 'center', block: 'nearest', behavior: 'smooth' });
});

async function selectCategory(id: number) {
  if (Date.now() - dragEndedAt < 300) return;
  selectedCategoryId.value = id;
  await loadAssets();
}

function triggerUpload() {
  if (selectedCategoryId.value == null) {
    ElMessage.warning('请先选择分类');
    return;
  }
  fileInput.value?.click();
}

async function onFilesPicked(ev: Event) {
  const input = ev.target as HTMLInputElement;
  const files = input.files ? Array.from(input.files) : [];
  input.value = '';
  if (!files.length || selectedCategoryId.value == null) return;
  uploading.value = true;
  try {
    const uploaded = await uploadAssets(selectedCategoryId.value, files);
    ElMessage.success(`已上传 ${uploaded.length} 份素材`);
    await loadAssets(uploaded[0]?.id ?? null);
  } catch (e) {
    ElMessage.error(apiError(e, '上传失败'));
  } finally {
    uploading.value = false;
  }
}

function selectAsset(id: number) {
  selectedAssetId.value = id;
}

function goPrev() {
  if (!canPrev.value) return;
  selectedAssetId.value = assets.value[currentIndex.value - 1].id;
}

function goNext() {
  if (!canNext.value) return;
  selectedAssetId.value = assets.value[currentIndex.value + 1].id;
}

function jumpToIndex() {
  const n = Number.parseInt(indexInput.value, 10);
  if (!Number.isInteger(n) || n < 1 || n > assets.value.length) {
    ElMessage.warning(
      assets.value.length === 0 ? '当前没有可预览的素材' : `请输入 1 到 ${assets.value.length} 的序号`,
    );
    indexInput.value = currentIndex.value >= 0 ? String(currentIndex.value + 1) : '';
    return;
  }
  selectedAssetId.value = assets.value[n - 1].id;
}

async function saveMeta() {
  if (!currentAsset.value) return;
  if (!form.displayName.trim()) {
    ElMessage.warning('请填写显示名称');
    return;
  }
  saving.value = true;
  try {
    const updated = await updateAsset(currentAsset.value.id, {
      displayName: form.displayName.trim(),
      description: form.description.trim() || null,
      chapterRefPlaceholder: form.chapterRefPlaceholder.trim() || null,
      tagNames: form.tagNames.map((t) => t.trim()).filter(Boolean),
      characterIds: [...form.characterIds],
    });
    ElMessage.success('已保存');
    await loadAssets(updated.id);
  } catch (e) {
    ElMessage.error(apiError(e, '保存失败'));
  } finally {
    saving.value = false;
  }
}

async function moveToRecycle() {
  if (!currentAsset.value) return;
  try {
    await ElMessageBox.confirm(
      `确认将「${currentAsset.value.displayName}」移入回收站？`,
      '移入回收站',
      { type: 'warning' },
    );
    recycling.value = true;
    await recycleAsset(currentAsset.value.id);
    ElMessage.success('已移入回收站');
    await loadAssets(null);
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return;
    ElMessage.error(apiError(e, '移入回收站失败'));
  } finally {
    recycling.value = false;
  }
}

function triggerReplace() {
  if (!currentAsset.value) return;
  if (currentAsset.value.status !== 'NORMAL') {
    ElMessage.warning('仅正常状态的素材可替换图片');
    return;
  }
  replaceFileInput.value?.click();
}

async function onReplaceFilePicked(ev: Event) {
  const input = ev.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || !currentAsset.value) return;
  replacing.value = true;
  try {
    const updated = await replaceAssetContent(currentAsset.value.id, file);
    previewBust.value = Date.now();
    ElMessage.success('图片已替换');
    await loadAssets(updated.id);
  } catch (e) {
    ElMessage.error(apiError(e, '替换失败'));
  } finally {
    replacing.value = false;
  }
}

function formatSize(bytes: number | null | undefined): string {
  if (bytes == null) return '-';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

let searchTimer: ReturnType<typeof setTimeout> | null = null;
watch(search, () => {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    void loadAssets();
  }, 250);
});

watch(characterFilter, () => {
  void loadAssets();
});

onMounted(async () => {
  try {
    const [chars] = await Promise.all([listCharacters(), loadCategories()]);
    characters.value = chars;
    await loadAssets();
  } catch (e) {
    ElMessage.error(apiError(e, '加载分类失败'));
  }
});
</script>

<template>
  <section class="workbench">
    <header class="toolbar">
      <div>
        <p class="eyebrow">ASSETS</p>
        <h2>素材管理</h2>
      </div>
      <div class="toolbar-actions">
        <el-select
          v-model="characterFilter"
          placeholder="人物筛选"
          style="width: 160px"
        >
          <el-option label="无关联" value="unlinked" />
          <el-option
            v-for="c in characters"
            :key="c.id"
            :label="c.name"
            :value="c.id!"
          />
          <el-option label="全部" value="all" />
        </el-select>
        <el-input
          v-model="search"
          clearable
          placeholder="搜索显示名 / 文件名 / 说明"
          style="width: 240px"
        />
        <input
          ref="fileInput"
          type="file"
          multiple
          accept=".jpg,.jpeg,.png,.webp,.gif,image/jpeg,image/png,image/webp,image/gif"
          hidden
          @change="onFilesPicked"
        />
        <el-button @click="router.push('/recycle')">回收站</el-button>
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="selectedCategoryId == null"
          @click="triggerUpload"
        >
          上传
        </el-button>
      </div>
    </header>

    <div class="layout">
      <div class="pane categories">
        <div class="pane-head">
          <strong>分类</strong>
          <el-button link type="primary" @click="router.push('/assets/categories')">去配置</el-button>
        </div>
        <ul>
          <li v-for="cat in categories" :key="cat.id">
            <draggable
              :list="bucketFor(cat.id)"
              item-key="id"
              :group="categoryGroup"
              :animation="150"
              :empty-insert-threshold="48"
              class="category-drop"
              :class="{ active: cat.id === selectedCategoryId, 'drop-ready': dragging }"
              @change="onDropOnCategory(cat.id, $event)"
              @click="selectCategory(cat.id)"
            >
              <template #header>
                <div class="cat-row">
                  <span class="cat-name">{{ cat.name }}</span>
                </div>
              </template>
              <template #item="{ element }">
                <span class="drop-chip">{{ element.displayName }}</span>
              </template>
            </draggable>
          </li>
        </ul>
        <p v-if="!categories.length" class="empty">
          暂无分类，请先到
          <el-button link type="primary" @click="router.push('/assets/categories')">管理配置</el-button>
          新增
        </p>
      </div>

      <section v-loading="loading" class="pane preview">
        <div v-if="currentAsset" class="preview-main">
          <img
            :src="assetContentUrl(currentAsset.id, previewBust || undefined)"
            :alt="currentAsset.displayName"
          />
          <div class="preview-meta">
            <strong>{{ currentAsset.displayName }}</strong>
            <span>第 {{ currentIndex + 1 }} / {{ assets.length }} 份</span>
          </div>
          <div class="nav">
            <el-button :disabled="!canPrev" @click="goPrev">上一份</el-button>
            <el-input
              v-model="indexInput"
              style="width: 88px"
              @keyup.enter="jumpToIndex"
              @blur="jumpToIndex"
            />
            <el-button @click="jumpToIndex">跳转</el-button>
            <el-button :disabled="!canNext" @click="goNext">下一份</el-button>
          </div>
        </div>
        <div v-else class="empty-preview">当前分类暂无素材，请先上传</div>
        <p v-if="isSearchActive" class="search-reorder-hint">
          搜索或人物非「全部」时不可在本分类内排序；人物选「全部」且清空搜索后可拖拽排序，仍可将素材拖到左侧其它分类。
        </p>
        <draggable
          v-model="assets"
          item-key="id"
          :group="thumbGroup"
          :sort="!isSearchActive"
          :animation="150"
          class="thumbs"
          ghost-class="thumb-ghost"
          @start="onDragStart"
          @end="onDragEnd"
          @change="onThumbsChange"
        >
          <template #item="{ element, index }">
            <button
              type="button"
              class="thumb"
              :class="{ active: element.id === selectedAssetId }"
              :data-thumb-id="element.id"
              :title="`${index + 1}. ${element.displayName}`"
              @click="selectAsset(element.id)"
            >
              <img
                :src="assetContentUrl(element.id, previewBust || undefined)"
                :alt="element.displayName"
              />
            </button>
          </template>
        </draggable>
      </section>

      <div class="pane editor">
        <div class="pane-head"><strong>属性</strong></div>
        <template v-if="currentAsset">
          <el-form label-position="top">
            <el-form-item label="显示名称" required>
              <el-input v-model="form.displayName" />
            </el-form-item>
            <el-form-item label="说明">
              <el-input v-model="form.description" type="textarea" :rows="4" />
            </el-form-item>
            <el-form-item label="篇章占位">
              <el-input v-model="form.chapterRefPlaceholder" placeholder="本期仅文本占位" />
            </el-form-item>
            <el-form-item label="标签">
              <el-select
                v-model="form.tagNames"
                multiple
                filterable
                allow-create
                default-first-option
                clearable
                placeholder="输入后回车创建标签"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="关联人物">
              <el-select
                v-model="form.characterIds"
                multiple
                filterable
                clearable
                placeholder="选择人物"
                style="width: 100%"
              >
                <el-option
                  v-for="c in characters"
                  :key="c.id"
                  :label="c.name"
                  :value="c.id!"
                />
              </el-select>
            </el-form-item>
          </el-form>
          <ul class="file-info">
            <li>原始文件：{{ currentAsset.originalFilename || '-' }}</li>
            <li>
              尺寸：
              {{ currentAsset.width && currentAsset.height ? `${currentAsset.width} × ${currentAsset.height}` : '-' }}
            </li>
            <li>大小：{{ formatSize(currentAsset.sizeBytes) }}</li>
            <li>类型：{{ currentAsset.contentType || '-' }}</li>
            <li>分类：{{ selectedCategory?.name || '-' }}</li>
          </ul>
          <input
            ref="replaceFileInput"
            type="file"
            accept=".jpg,.jpeg,.png,.webp,.gif,image/jpeg,image/png,image/webp,image/gif"
            hidden
            @change="onReplaceFilePicked"
          />
          <div class="editor-actions">
            <el-button type="primary" :loading="saving" @click="saveMeta">保存</el-button>
            <el-button
              :loading="replacing"
              :disabled="currentAsset.status !== 'NORMAL'"
              @click="triggerReplace"
            >
              替换图片
            </el-button>
            <el-button type="danger" plain :loading="recycling" @click="moveToRecycle">
              移入回收站
            </el-button>
          </div>
        </template>
        <p v-else class="empty">选中素材后可编辑名称与说明</p>
      </div>
    </div>

  </section>
</template>

<style scoped>
.workbench {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 112px);
}
.toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}
.toolbar h2 {
  margin: 8px 0 0;
}
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.editor-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 280px;
  gap: 16px;
  flex: 1;
  min-height: 0;
}
.pane {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 10px 30px #24325212;
  padding: 16px;
  min-height: 520px;
}
.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.categories ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.category-drop {
  min-height: 40px;
  border-radius: 10px;
  cursor: pointer;
}
.category-drop.active {
  background: #eef3ff;
  color: #3b5bcc;
  font-weight: 600;
}
.category-drop.drop-ready {
  outline: 1px dashed #9aa8d9;
  outline-offset: -1px;
}
.cat-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
}
.drop-chip {
  display: none;
}
.cat-name {
  color: #172033;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.category-drop.active .cat-name {
  color: #3b5bcc;
}
.preview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.preview-main {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-height: 0;
}
.preview-main img {
  max-width: 100%;
  max-height: 360px;
  object-fit: contain;
  background: #f4f6fa;
  border-radius: 12px;
}
.preview-meta {
  display: flex;
  gap: 12px;
  align-items: baseline;
  color: #6f7e9d;
}
.nav {
  display: flex;
  gap: 8px;
  align-items: center;
}
.empty-preview,
.empty {
  color: #6f7e9d;
  padding: 24px 8px;
}
.search-reorder-hint {
  margin: 0;
  font-size: 12px;
  color: #9aa8d9;
  line-height: 1.5;
}
.thumbs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 6px;
  min-height: 80px;
}
.thumb {
  flex: 0 0 auto;
  width: 72px;
  height: 72px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: 10px;
  overflow: hidden;
  cursor: grab;
  background: #f4f6fa;
}
.thumb-ghost {
  opacity: 0.4;
}
.thumb.active {
  border-color: #3b5bcc;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.file-info {
  list-style: none;
  padding: 0;
  margin: 0 0 16px;
  color: #6f7e9d;
  font-size: 13px;
  line-height: 1.8;
}
@media (max-width: 1100px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
