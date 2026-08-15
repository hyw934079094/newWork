<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { assetContentUrl, listAssets, type AssetItem } from '../../api/asset';
import { listCategories, type AssetCategoryItem } from '../../api/category';
import {
  createSeries,
  deleteSeries,
  listSeries,
  updateSeries,
  type SeriesItem,
  type SeriesStatus,
} from '../../api/series';
import ImageLightbox from '../../components/ImageLightbox.vue';

const router = useRouter();

const STATUS_OPTIONS: { value: SeriesStatus; label: string }[] = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'SERIALIZING', label: '连载中' },
  { value: 'COMPLETED', label: '已完结' },
  { value: 'PUBLISHED', label: '已发布' },
];

const loading = ref(false);
const rows = ref<SeriesItem[]>([]);
const dialogVisible = ref(false);
const editing = ref(false);
const editingId = ref<number | null>(null);
const saving = ref(false);

const pickerVisible = ref(false);
const pickerCategoryId = ref<number | 'all'>('all');
const pickerKeyword = ref('');
const pickerAssets = ref<AssetItem[]>([]);
const pickerSelectedId = ref<number | null>(null);
const pickerLoading = ref(false);
const categories = ref<AssetCategoryItem[]>([]);

const previewVisible = ref(false);
const previewAssetId = ref<number | null>(null);
const previewAlt = ref('');

function openCoverPreview(assetId: number, alt?: string) {
  previewAssetId.value = assetId;
  previewAlt.value = alt ?? '';
  previewVisible.value = true;
}

const filters = reactive({
  q: '',
  status: '' as '' | SeriesStatus,
});

const form = reactive({
  name: '',
  status: 'DRAFT' as SeriesStatus,
  summary: '',
  tags: '',
  coverAssetId: null as number | null,
});

const statusLabel = computed(() => {
  const map = Object.fromEntries(STATUS_OPTIONS.map((o) => [o.value, o.label])) as Record<
    SeriesStatus,
    string
  >;
  return (status: SeriesStatus) => map[status] ?? status;
});

function resetForm() {
  form.name = '';
  form.status = 'DRAFT';
  form.summary = '';
  form.tags = '';
  form.coverAssetId = null;
}

function resetFilters() {
  filters.q = '';
  filters.status = '';
}

async function load() {
  loading.value = true;
  try {
    rows.value = await listSeries({
      q: filters.q.trim() || undefined,
      status: filters.status || undefined,
    });
  } catch {
    ElMessage.error('加载系列失败');
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editing.value = false;
  editingId.value = null;
  resetForm();
  dialogVisible.value = true;
}

function openEdit(row: SeriesItem) {
  editing.value = true;
  editingId.value = row.id ?? null;
  form.name = row.name ?? '';
  form.status = row.status ?? 'DRAFT';
  form.summary = row.summary ?? '';
  form.tags = row.tags ?? '';
  form.coverAssetId = row.coverAssetId ?? null;
  dialogVisible.value = true;
}

function clearCover() {
  form.coverAssetId = null;
}

async function openCoverPicker() {
  pickerSelectedId.value = form.coverAssetId;
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

function selectPickerAsset(id: number) {
  pickerSelectedId.value = pickerSelectedId.value === id ? null : id;
}

function isPickerSelected(id: number): boolean {
  return pickerSelectedId.value === id;
}

function confirmCoverPicker() {
  form.coverAssetId = pickerSelectedId.value;
  pickerVisible.value = false;
}

function payload() {
  return {
    name: form.name.trim(),
    status: form.status,
    summary: form.summary.trim() || null,
    tags: form.tags.trim() || null,
    coverAssetId: form.coverAssetId,
  };
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写系列名称');
    return;
  }
  saving.value = true;
  try {
    if (editing.value && editingId.value != null) {
      await updateSeries(editingId.value, payload());
      ElMessage.success('已更新');
    } else {
      await createSeries(payload());
      ElMessage.success('已创建');
    }
    dialogVisible.value = false;
    await load();
  } catch (e: unknown) {
    const msg =
      (e as { response?: { data?: { message?: string } } })?.response?.data?.message || '保存失败';
    ElMessage.error(msg);
  } finally {
    saving.value = false;
  }
}

function apiError(e: unknown, fallback: string): string {
  const err = e as { response?: { status?: number; data?: { message?: string } } };
  const msg = err?.response?.data?.message?.trim();
  if (msg) return msg;
  if (err?.response?.status === 409) return '该系列下仍有篇章，无法删除';
  return fallback;
}

function goArcs(row: SeriesItem) {
  if (row.id == null) return;
  void router.push(`/series/${row.id}/arcs`);
}

async function remove(row: SeriesItem) {
  if (row.id == null) return;
  try {
    await ElMessageBox.confirm(`确认删除系列「${row.name}」？`, '删除确认', { type: 'warning' });
    await deleteSeries(row.id);
    ElMessage.success('已删除');
    await load();
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return;
    ElMessage.error(apiError(e, '删除失败'));
  }
}

onMounted(load);
</script>

<template>
  <section class="series-page">
    <div class="header">
      <div>
        <p class="eyebrow">SERIES</p>
        <h2>故事系列</h2>
      </div>
      <el-button type="primary" @click="openCreate">新增系列</el-button>
    </div>

    <el-form class="filters" :inline="true" @submit.prevent="load">
      <el-form-item label="关键词">
        <el-input
          v-model="filters.q"
          clearable
          placeholder="名称 / 编号 / 标签"
          class="filter-control filter-control--wide"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select
          v-model="filters.status"
          clearable
          placeholder="全部"
          class="filter-control"
        >
          <el-option
            v-for="opt in STATUS_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item class="filters-actions">
        <el-button type="primary" :loading="loading" native-type="submit">查询</el-button>
        <el-button
          @click="
            resetFilters();
            load();
          "
        >
          重置
        </el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows" stripe empty-text="暂无系列">
      <el-table-column label="封面" width="88">
        <template #default="{ row }">
          <button
            v-if="row.coverAssetId != null"
            type="button"
            class="cover-thumb-btn"
            title="查看大图"
            @click="openCoverPreview(row.coverAssetId, row.name)"
          >
            <img
              class="cover-thumb"
              :src="assetContentUrl(row.coverAssetId)"
              :alt="row.name"
            />
          </button>
          <span v-else class="cover-placeholder">无</span>
        </template>
      </el-table-column>
      <el-table-column prop="code" label="编号" width="100" />
      <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          {{ statusLabel(row.status) }}
        </template>
      </el-table-column>
      <el-table-column prop="tags" label="标签" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button link type="primary" @click="goArcs(row)">篇章</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑系列' : '新增系列'"
      width="780px"
      destroy-on-close
      class="series-edit-dialog"
    >
      <el-form label-width="88px" class="edit-form">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如 暗夜物语" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" class="status-select">
            <el-option
              v-for="opt in STATUS_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.summary" type="textarea" :rows="3" placeholder="公开简介，可选" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="逗号分隔，如 奇幻,连载" />
        </el-form-item>
        <el-form-item label="封面">
          <div class="cover-editor">
            <div class="cover-preview">
              <img
                v-if="form.coverAssetId != null"
                :src="assetContentUrl(form.coverAssetId)"
                alt="封面预览"
              />
              <span v-else class="cover-placeholder">暂无封面</span>
            </div>
            <div class="cover-actions">
              <el-button @click="openCoverPicker">选择封面</el-button>
              <el-button :disabled="form.coverAssetId == null" @click="clearCover">清除</el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="pickerVisible"
      title="选择封面"
      width="760px"
      append-to-body
      destroy-on-close
      class="asset-picker-dialog"
    >
      <div class="picker-toolbar">
        <el-select
          v-model="pickerCategoryId"
          placeholder="全部分类"
          class="picker-category"
          @change="loadPickerAssets"
        >
          <el-option label="全部分类" value="all" />
          <el-option
            v-for="cat in categories"
            :key="cat.id"
            :label="cat.name"
            :value="cat.id"
          />
        </el-select>
        <el-input
          v-model="pickerKeyword"
          clearable
          placeholder="关键字（显示名）"
          class="picker-keyword"
          @clear="loadPickerAssets"
          @keyup.enter="loadPickerAssets"
        />
        <el-button type="primary" :loading="pickerLoading" @click="loadPickerAssets">
          筛选
        </el-button>
      </div>

      <div v-loading="pickerLoading" class="picker-grid">
        <button
          v-for="asset in pickerAssets"
          :key="asset.id"
          type="button"
          class="picker-card"
          :class="{ 'is-selected': isPickerSelected(asset.id) }"
          @click="selectPickerAsset(asset.id)"
        >
          <img :src="assetContentUrl(asset.id)" :alt="asset.displayName" />
          <span class="picker-card-title" :title="asset.displayName">{{ asset.displayName }}</span>
        </button>
        <p v-if="!pickerLoading && !pickerAssets.length" class="hint picker-empty">暂无 NORMAL 素材</p>
      </div>

      <template #footer>
        <div class="picker-footer">
          <span class="picker-count">
            {{ pickerSelectedId != null ? '已选 1 项' : '未选择' }}
          </span>
          <div class="picker-footer-actions">
            <el-button @click="pickerVisible = false">取消</el-button>
            <el-button type="primary" @click="confirmCoverPicker">确定</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <ImageLightbox
      v-model="previewVisible"
      :src="previewAssetId != null ? assetContentUrl(previewAssetId) : null"
      :alt="previewAlt"
    />
  </section>
</template>

<style scoped>
.series-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}
.eyebrow {
  margin: 0;
  color: #6f7e9d;
  font-size: 12px;
  letter-spacing: 0.08em;
}
.header h2 {
  margin: 8px 0 0;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 4px 12px;
  padding: 16px 16px 4px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 10px 30px #24325212;
}
.filters :deep(.el-form-item) {
  margin-right: 0;
  margin-bottom: 12px;
}
.filters :deep(.el-form-item__label) {
  white-space: nowrap;
}
.filters :deep(.el-form-item__content) {
  flex-wrap: nowrap;
}
.filter-control {
  width: 140px;
  min-width: 140px;
}
.filter-control--wide {
  width: 220px;
  min-width: 220px;
}
.filters-actions {
  margin-left: auto;
}
.cover-thumb-btn {
  padding: 0;
  border: 0;
  background: transparent;
  cursor: zoom-in;
  display: inline-flex;
  border-radius: 8px;
}
.cover-thumb-btn:focus-visible {
  outline: 2px solid #2f6fed;
  outline-offset: 2px;
}
.cover-thumb {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 8px;
  background: #eef1f7;
  display: block;
}
.cover-placeholder {
  color: #9aa6bf;
  font-size: 13px;
}
.row-actions {
  display: inline-flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 2px;
  white-space: nowrap;
}
.status-select {
  width: 180px;
}
.cover-editor {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
.cover-preview {
  width: 120px;
  height: 120px;
  border-radius: 12px;
  background: #f4f6fa;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.cover-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.hint {
  margin: 0;
  color: #6f7e9d;
  font-size: 13px;
}
.picker-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.picker-category {
  width: 168px;
  flex-shrink: 0;
}
.picker-keyword {
  flex: 1;
  min-width: 160px;
}
.picker-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, 112px);
  gap: 12px;
  min-height: 200px;
  max-height: 420px;
  overflow-y: auto;
  padding: 4px 2px 8px;
}
.picker-card {
  width: 112px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 4px 14px #24325214;
  cursor: pointer;
  overflow: hidden;
  text-align: left;
  font: inherit;
  color: inherit;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.picker-card:hover {
  border-color: #a8c0f5;
}
.picker-card.is-selected {
  border-color: #3a6ff0;
  box-shadow: 0 0 0 1px #3a6ff0, 0 4px 14px #24325218;
}
.picker-card img {
  width: 108px;
  height: 108px;
  object-fit: cover;
  display: block;
  background: #eef1f7;
}
.picker-card-title {
  display: block;
  padding: 6px 8px 8px;
  font-size: 12px;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.picker-empty {
  grid-column: 1 / -1;
  margin: 24px 0;
  text-align: center;
}
.picker-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.picker-count {
  color: #6f7e9d;
  font-size: 13px;
}
.picker-footer-actions {
  display: flex;
  gap: 8px;
}
</style>
