<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getArc } from '../../api/arc';
import {
  createPage,
  deletePage,
  listPages,
  reorderPages,
  toContentJsonString,
  updatePage,
  type PageItem,
} from '../../api/page';

const route = useRoute();
const router = useRouter();
const arcId = computed(() => Number(route.params.arcId));
const arcTitle = ref('');
const seriesId = ref<number | null>(null);
const loading = ref(false);
const allRows = ref<PageItem[]>([]);
const rows = computed(() => {
  const q = filters.q.trim();
  return q ? allRows.value.filter((row) => (row.title ?? '').includes(q)) : allRows.value;
});
const dialogVisible = ref(false);
const editing = ref(false);
const editingId = ref<number | null>(null);
const editingContentJson = ref('[]');
const saving = ref(false);
const reordering = ref(false);

const filters = reactive({
  q: '',
});

const form = reactive({
  title: '',
});

function apiError(e: unknown, fallback: string): string {
  const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
  return msg?.trim() || fallback;
}

function resetForm() {
  form.title = '';
  editingContentJson.value = '[]';
}

function resetFilters() {
  filters.q = '';
}

async function loadArc() {
  const id = arcId.value;
  if (!Number.isFinite(id) || id <= 0) {
    arcTitle.value = '';
    seriesId.value = null;
    return;
  }
  try {
    const arc = await getArc(id);
    arcTitle.value = arc.title ?? '';
    seriesId.value = arc.seriesId ?? null;
  } catch {
    arcTitle.value = '';
    seriesId.value = null;
    ElMessage.error('加载篇章失败');
  }
}

async function load() {
  const id = arcId.value;
  if (!Number.isFinite(id) || id <= 0) {
    allRows.value = [];
    return;
  }
  loading.value = true;
  try {
    allRows.value = await listPages(id);
  } catch (e: unknown) {
    ElMessage.error(apiError(e, '加载页面失败'));
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

function openEdit(row: PageItem) {
  editing.value = true;
  editingId.value = row.id ?? null;
  form.title = row.title ?? '';
  editingContentJson.value = toContentJsonString(row.contentJson);
  dialogVisible.value = true;
}

async function submit() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写页面标题');
    return;
  }
  const id = arcId.value;
  if (!Number.isFinite(id) || id <= 0) {
    ElMessage.error('篇章无效');
    return;
  }
  saving.value = true;
  try {
    if (editing.value && editingId.value != null) {
      await updatePage(editingId.value, {
        title: form.title.trim(),
        contentJson: toContentJsonString(editingContentJson.value),
      });
      ElMessage.success('已更新');
    } else {
      await createPage(id, { title: form.title.trim() });
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

async function remove(row: PageItem) {
  if (row.id == null) return;
  try {
    await ElMessageBox.confirm(`确认删除页面「${row.title}」？`, '删除确认', { type: 'warning' });
    await deletePage(row.id);
    ElMessage.success('已删除');
    await load();
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return;
    ElMessage.error(apiError(e, '删除失败'));
  }
}

function rowIndex(row: PageItem): number {
  return allRows.value.findIndex((item) => item.id === row.id);
}

function canMoveUp(row: PageItem): boolean {
  return !reordering.value && rowIndex(row) > 0;
}

function canMoveDown(row: PageItem): boolean {
  const idx = rowIndex(row);
  return !reordering.value && idx >= 0 && idx < allRows.value.length - 1;
}

function goEditor(row: PageItem) {
  if (row.id == null) return;
  void router.push(`/pages/${row.id}/edit`);
}

function goArcs() {
  if (seriesId.value != null) {
    void router.push(`/series/${seriesId.value}/arcs`);
    return;
  }
  void router.back();
}

function goArcPreview() {
  const id = arcId.value;
  if (!Number.isFinite(id) || id <= 0) return;
  void router.push({ path: `/arcs/${id}/preview`, query: { from: 'pages' } });
}

async function move(row: PageItem, dir: -1 | 1) {
  if (row.id == null || reordering.value) return;
  const idx = allRows.value.findIndex((item) => item.id === row.id);
  const next = idx + dir;
  if (idx < 0 || next < 0 || next >= allRows.value.length) return;
  const ids = allRows.value.map((item) => item.id).filter((id): id is number => id != null);
  if (ids.length !== allRows.value.length) return;
  const swapped = [...ids];
  const currentId = swapped[idx];
  const neighborId = swapped[next];
  if (currentId == null || neighborId == null) return;
  swapped[idx] = neighborId;
  swapped[next] = currentId;
  const arc = arcId.value;
  reordering.value = true;
  try {
    await reorderPages(arc, swapped);
    ElMessage.success('已调整顺序');
    await load();
  } catch (e: unknown) {
    ElMessage.error(apiError(e, '调整顺序失败'));
  } finally {
    reordering.value = false;
  }
}

watch(arcId, async () => {
  await loadArc();
  await load();
});

onMounted(async () => {
  await loadArc();
  await load();
});
</script>

<template>
  <section class="page-list-page">
    <div class="header">
      <div>
        <p class="eyebrow">PAGES</p>
        <h2>{{ arcTitle ? `${arcTitle} · 页面` : '页面' }}</h2>
      </div>
      <div class="header-actions">
        <el-button @click="goArcs">返回篇章</el-button>
        <el-button @click="goArcPreview">整篇预览</el-button>
        <el-button type="primary" @click="openCreate">新增页面</el-button>
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

    <el-table v-loading="loading" :data="rows" stripe empty-text="暂无页面">
      <el-table-column prop="sortOrder" label="顺序" width="88" />
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button link type="primary" @click="goEditor(row)">编辑器</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" :disabled="!canMoveUp(row)" @click="move(row, -1)">
              上移
            </el-button>
            <el-button link type="primary" :disabled="!canMoveDown(row)" @click="move(row, 1)">
              下移
            </el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑页面' : '新增页面'"
      width="520px"
      destroy-on-close
    >
      <el-form label-width="88px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="如 开场" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page-list-page {
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
.row-actions {
  display: inline-flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 2px;
  white-space: nowrap;
}
</style>
