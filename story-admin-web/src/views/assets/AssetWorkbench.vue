<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  assetContentUrl,
  listAssets,
  updateAsset,
  uploadAssets,
  type AssetItem,
} from '../../api/asset';
import {
  createCategory,
  deleteCategory,
  listCategories,
  updateCategory,
  type AssetCategoryItem,
} from '../../api/category';

const loading = ref(false);
const uploading = ref(false);
const saving = ref(false);
const categories = ref<AssetCategoryItem[]>([]);
const assets = ref<AssetItem[]>([]);
const selectedCategoryId = ref<number | null>(null);
const selectedAssetId = ref<number | null>(null);
const search = ref('');
const indexInput = ref('1');
const fileInput = ref<HTMLInputElement | null>(null);

const categoryDialogVisible = ref(false);
const categoryEditing = ref(false);
const categoryForm = reactive({
  id: null as number | null,
  code: '',
  name: '',
});

const form = reactive({
  displayName: '',
  description: '',
  chapterRefPlaceholder: '',
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

function apiError(e: unknown, fallback: string): string {
  const err = e as { response?: { data?: { message?: string } } };
  return err?.response?.data?.message || fallback;
}

async function loadCategories(preferId?: number | null) {
  categories.value = await listCategories();
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
    assets.value = await listAssets({
      categoryId: selectedCategoryId.value,
      status: 'NORMAL',
      q: search.value.trim() || undefined,
    });
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
  indexInput.value = currentIndex.value >= 0 ? String(currentIndex.value + 1) : '';
}

watch(currentAsset, syncForm, { immediate: true });

watch(selectedAssetId, async () => {
  await nextTick();
  const el = document.querySelector<HTMLElement>(`[data-thumb-id="${selectedAssetId.value}"]`);
  el?.scrollIntoView({ inline: 'center', block: 'nearest', behavior: 'smooth' });
});

async function selectCategory(id: number) {
  selectedCategoryId.value = id;
  await loadAssets();
}

function openCreateCategory() {
  categoryEditing.value = false;
  categoryForm.id = null;
  categoryForm.code = '';
  categoryForm.name = '';
  categoryDialogVisible.value = true;
}

function openEditCategory(row: AssetCategoryItem) {
  categoryEditing.value = true;
  categoryForm.id = row.id;
  categoryForm.code = row.code;
  categoryForm.name = row.name;
  categoryDialogVisible.value = true;
}

async function submitCategory() {
  if (!categoryForm.name.trim()) {
    ElMessage.warning('请填写分类名称');
    return;
  }
  if (!categoryEditing.value && !categoryForm.code.trim()) {
    ElMessage.warning('请填写分类编码');
    return;
  }
  try {
    if (categoryEditing.value && categoryForm.id != null) {
      const updated = await updateCategory(categoryForm.id, { name: categoryForm.name.trim() });
      ElMessage.success('分类已更新');
      categoryDialogVisible.value = false;
      await loadCategories(updated.id);
    } else {
      const created = await createCategory({
        code: categoryForm.code.trim(),
        name: categoryForm.name.trim(),
      });
      ElMessage.success('分类已新增');
      categoryDialogVisible.value = false;
      await loadCategories(created.id);
      await loadAssets();
    }
  } catch (e) {
    ElMessage.error(apiError(e, '保存分类失败'));
  }
}

async function removeCategory(row: AssetCategoryItem) {
  if (row.systemPreset) {
    ElMessage.warning('预置分类不可删除');
    return;
  }
  try {
    await ElMessageBox.confirm(`确认删除分类「${row.name}」？`, '删除确认', { type: 'warning' });
    await deleteCategory(row.id);
    ElMessage.success('已删除分类');
    if (selectedCategoryId.value === row.id) {
      selectedCategoryId.value = null;
    }
    await loadCategories();
    await loadAssets();
  } catch (e) {
    if (e === 'cancel' || e === 'close') return;
    ElMessage.error(apiError(e, '删除分类失败'));
  }
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
    });
    ElMessage.success('已保存');
    await loadAssets(updated.id);
  } catch (e) {
    ElMessage.error(apiError(e, '保存失败'));
  } finally {
    saving.value = false;
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

onMounted(async () => {
  try {
    await loadCategories();
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
      <aside class="pane categories">
        <div class="pane-head">
          <strong>分类</strong>
          <el-button link type="primary" @click="openCreateCategory">新增</el-button>
        </div>
        <ul>
          <li
            v-for="cat in categories"
            :key="cat.id"
            :class="{ active: cat.id === selectedCategoryId }"
            @click="selectCategory(cat.id)"
          >
            <span class="cat-name">{{ cat.name }}</span>
            <span class="cat-actions">
              <el-button link type="primary" @click.stop="openEditCategory(cat)">改名</el-button>
              <el-button
                v-if="!cat.systemPreset"
                link
                type="danger"
                @click.stop="removeCategory(cat)"
              >
                删
              </el-button>
            </span>
          </li>
        </ul>
        <p v-if="!categories.length" class="empty">暂无分类</p>
      </aside>

      <section v-loading="loading" class="pane preview">
        <div v-if="currentAsset" class="preview-main">
          <img :src="assetContentUrl(currentAsset.id)" :alt="currentAsset.displayName" />
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
        <div class="thumbs">
          <button
            v-for="(item, idx) in assets"
            :key="item.id"
            type="button"
            class="thumb"
            :class="{ active: item.id === selectedAssetId }"
            :data-thumb-id="item.id"
            :title="`${idx + 1}. ${item.displayName}`"
            @click="selectAsset(item.id)"
          >
            <img :src="assetContentUrl(item.id)" :alt="item.displayName" />
          </button>
        </div>
      </section>

      <aside class="pane editor">
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
          <el-button type="primary" :loading="saving" @click="saveMeta">保存</el-button>
        </template>
        <p v-else class="empty">选中素材后可编辑名称与说明</p>
      </aside>
    </div>

    <el-dialog
      v-model="categoryDialogVisible"
      :title="categoryEditing ? '编辑分类' : '新增分类'"
      width="420px"
    >
      <el-form label-width="88px">
        <el-form-item label="编码" required>
          <el-input v-model="categoryForm.code" :disabled="categoryEditing" placeholder="如 location" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="categoryForm.name" placeholder="如 地点" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCategory">保存</el-button>
      </template>
    </el-dialog>
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
.categories li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  cursor: pointer;
}
.categories li.active {
  background: #eef3ff;
  color: #3b5bcc;
  font-weight: 600;
}
.cat-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cat-actions {
  display: flex;
  flex-shrink: 0;
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
.thumbs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 6px;
}
.thumb {
  flex: 0 0 auto;
  width: 72px;
  height: 72px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  background: #f4f6fa;
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
