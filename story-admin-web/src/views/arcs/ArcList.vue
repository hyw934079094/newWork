<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { assetContentUrl, listAssets, ASSET_PAGE_SIZE, type AssetItem } from '../../api/asset';
import { listCategories, type AssetCategoryItem } from '../../api/category';
import {
  arcReadingStreamUrl,
  createArc,
  deleteArc,
  listArcs,
  updateArc,
  type ArcItem,
  type ArcStatus,
} from '../../api/arc';
import { getSeries } from '../../api/series';
import AssetThumb from '../../components/AssetThumb.vue';

const STATUS_OPTIONS: { value: ArcStatus; label: string }[] = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'WRITING', label: '撰写中' },
  { value: 'FINALIZED', label: '已定稿' },
];

const route = useRoute();
const router = useRouter();
const seriesId = computed(() => Number(route.params.seriesId));
const seriesName = ref('');
const loading = ref(false);
const rows = ref<ArcItem[]>([]);
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
const pickerLoadingMore = ref(false);
const pickerPage = ref(0);
const pickerTotal = ref(0);
const categories = ref<AssetCategoryItem[]>([]);

const filters = reactive({
  q: '',
  status: '' as '' | ArcStatus,
});

const form = reactive({
  title: '',
  status: 'DRAFT' as ArcStatus,
  summary: '',
  coverAssetId: null as number | null,
});

const statusLabel = computed(() => {
  const map = Object.fromEntries(STATUS_OPTIONS.map((o) => [o.value, o.label])) as Record<
    ArcStatus,
    string
  >;
  return (status: ArcStatus) => map[status] ?? status;
});

function apiError(e: unknown, fallback: string): string {
  const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
  return msg?.trim() || fallback;
}

function resetForm() {
  form.title = '';
  form.status = 'DRAFT';
  form.summary = '';
  form.coverAssetId = null;
}

function resetFilters() {
  filters.q = '';
  filters.status = '';
}

async function loadSeries() {
  const id = seriesId.value;
  if (!Number.isFinite(id) || id <= 0) {
    seriesName.value = '';
    return;
  }
  try {
    const series = await getSeries(id);
    seriesName.value = series.name ?? '';
  } catch {
    seriesName.value = '';
    ElMessage.error('加载系列失败');
  }
}

async function load() {
  const id = seriesId.value;
  if (!Number.isFinite(id) || id <= 0) {
    rows.value = [];
    return;
  }
  loading.value = true;
  try {
    const list = await listArcs(id, {
      q: filters.q.trim() || undefined,
    });
    rows.value = filters.status ? list.filter((row) => row.status === filters.status) : list;
  } catch (e: unknown) {
    ElMessage.error(apiError(e, '加载篇章失败'));
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

function openEdit(row: ArcItem) {
  editing.value = true;
  editingId.value = row.id ?? null;
  form.title = row.title ?? '';
  form.status = row.status ?? 'DRAFT';
  form.summary = row.summary ?? '';
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
  pickerPage.value = 0;
  pickerLoading.value = true;
  try {
    const data = await listAssets({
      status: 'NORMAL',
      categoryId: pickerCategoryId.value === 'all' ? undefined : pickerCategoryId.value,
      q: pickerKeyword.value.trim() || undefined,
      page: 0,
      size: ASSET_PAGE_SIZE,
    });
    pickerAssets.value = data.items;
    pickerTotal.value = data.total;
  } finally {
    pickerLoading.value = false;
  }
}

async function loadMorePickerAssets() {
  if (pickerAssets.value.length >= pickerTotal.value || pickerLoadingMore.value) return;
  pickerLoadingMore.value = true;
  try {
    pickerPage.value += 1;
    const data = await listAssets({
      status: 'NORMAL',
      categoryId: pickerCategoryId.value === 'all' ? undefined : pickerCategoryId.value,
      q: pickerKeyword.value.trim() || undefined,
      page: pickerPage.value,
      size: ASSET_PAGE_SIZE,
    });
    pickerAssets.value = [...pickerAssets.value, ...data.items];
    pickerTotal.value = data.total;
  } finally {
    pickerLoadingMore.value = false;
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
    title: form.title.trim(),
    status: form.status,
    summary: form.summary.trim() || null,
    coverAssetId: form.coverAssetId,
  };
}

async function submit() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写篇章标题');
    return;
  }
  const id = seriesId.value;
  if (!Number.isFinite(id) || id <= 0) {
    ElMessage.error('系列无效');
    return;
  }
  saving.value = true;
  try {
    if (editing.value && editingId.value != null) {
      await updateArc(editingId.value, payload());
      ElMessage.success('已更新');
    } else {
      await createArc(id, payload());
      ElMessage.success('已创建');
    }
    dialogVisible.value = false;
    await load();
  } catch (e: unknown) {
    ElMessage.error(apiError(e, '保存失败'));
  } finally {
    saving.value = false;
  }
}

async function remove(row: ArcItem) {
  if (row.id == null) return;
  try {
    await ElMessageBox.confirm(
      `确认删除篇章「${row.title}」？将级联删除其下所有页面。`,
      '删除确认',
      { type: 'warning' },
    );
    await deleteArc(row.id);
    ElMessage.success('已删除');
    await load();
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return;
    ElMessage.error(apiError(e, '删除失败'));
  }
}

function goPages(row: ArcItem) {
  if (row.id == null) return;
  void router.push(`/arcs/${row.id}/pages`);
}

function goPreview(row: ArcItem) {
  if (row.id == null) return;
  void router.push(`/arcs/${row.id}/preview`);
}

async function copyReadingStreamUrl(row: ArcItem) {
  if (row.id == null) {
    ElMessage.warning('无效的篇章');
    return;
  }
  const url = `${window.location.origin}${arcReadingStreamUrl(row.id)}`;
  try {
    await navigator.clipboard.writeText(url);
    ElMessage.success('AI 阅读流链接已复制');
  } catch {
    ElMessage.error('复制失败，请手动复制');
  }
}

function goSeries() {
  void router.push('/series');
}

watch(seriesId, async () => {
  await loadSeries();
  await load();
});

onMounted(async () => {
  await loadSeries();
  await load();
});
</script>

<template>
  <section class="arc-page">
    <div class="header">
      <div>
        <p class="eyebrow">ARCS</p>
        <h2>{{ seriesName ? `${seriesName} · 篇章` : '篇章' }}</h2>
      </div>
      <div class="header-actions">
        <el-button @click="goSeries">返回系列</el-button>
        <el-button type="primary" @click="openCreate">新增篇章</el-button>
      </div>
    </div>

    <el-form class="filters" :inline="true" @submit.prevent="load">
      <el-form-item label="关键词">
        <el-input
          v-model="filters.q"
          clearable
          placeholder="标题"
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

    <el-table v-loading="loading" :data="rows" stripe empty-text="暂无篇章">
      <el-table-column label="封面" width="88">
        <template #default="{ row }">
          <AssetThumb
            v-if="row.coverAssetId != null"
            :asset-id="row.coverAssetId"
            :alt="row.title"
            :size="48"
          />
          <span v-else class="cover-placeholder">无</span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          {{ statusLabel(row.status) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="summary"
        label="简介"
        min-width="180"
        :show-overflow-tooltip="{
          popperClass: 'arc-summary-tooltip',
          placement: 'top',
        }"
      />
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button link type="primary" @click="goPages(row)">页面</el-button>
            <el-button link type="primary" @click="goPreview(row)">预览</el-button>
            <el-button link type="primary" @click="copyReadingStreamUrl(row)">复制</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑篇章' : '新增篇章'"
      width="780px"
      destroy-on-close
      class="arc-edit-dialog"
    >
      <el-form label-width="88px" class="edit-form">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="如 暗夜开篇" />
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
          <el-input v-model="form.summary" type="textarea" :rows="3" placeholder="篇章简介，可选" />
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
      <div v-if="pickerAssets.length < pickerTotal" class="picker-more">
        <el-button :loading="pickerLoadingMore" @click="loadMorePickerAssets">加载更多</el-button>
        <span class="hint">已加载 {{ pickerAssets.length }} / {{ pickerTotal }}</span>
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
  </section>
</template>

<style scoped>
.arc-page {
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
.header-actions {
  display: flex;
  gap: 8px;
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
.picker-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px 0 4px;
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

<style>
/* Tooltip teleports to body — keep a readable fixed width */
.arc-summary-tooltip {
  max-width: 360px !important;
  width: 360px;
  white-space: pre-wrap !important;
  word-break: break-word;
  line-height: 1.5;
}
</style>
