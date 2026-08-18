<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  assetContentUrl,
  hardDeleteAsset,
  listAssets,
  restoreAsset,
  ASSET_MAX_PAGE_SIZE,
  ASSET_PAGE_SIZE,
  type AssetItem,
} from '../../api/asset';

const loading = ref(false);
const bulkWorking = ref(false);
const rows = ref<AssetItem[]>([]);
const page = ref(0);
const pageSize = ASSET_PAGE_SIZE;
const total = ref(0);

const isEmpty = computed(() => total.value === 0);

function apiError(e: unknown, fallback: string): string {
  const err = e as { response?: { data?: { message?: string } } };
  return err?.response?.data?.message || fallback;
}

async function load() {
  loading.value = true;
  try {
    let data = await listAssets({ status: 'DELETED', page: page.value, size: pageSize });
    if (data.items.length === 0 && data.total > 0 && page.value > 0) {
      page.value = Math.max(0, Math.ceil(data.total / pageSize) - 1);
      data = await listAssets({ status: 'DELETED', page: page.value, size: pageSize });
    }
    rows.value = data.items;
    total.value = data.total;
  } catch (e) {
    ElMessage.error(apiError(e, '加载回收站失败'));
  } finally {
    loading.value = false;
  }
}

function onPageChange(nextPage: number) {
  const next = nextPage - 1;
  if (next === page.value) return;
  page.value = next;
  void load();
}

async function fetchAllDeleted(): Promise<AssetItem[]> {
  const size = Math.min(Math.max(total.value, 1), ASSET_MAX_PAGE_SIZE);
  const data = await listAssets({ status: 'DELETED', page: 0, size });
  return data.items;
}

async function restore(row: AssetItem) {
  try {
    await restoreAsset(row.id);
    ElMessage.success('已恢复');
    await load();
  } catch (e) {
    ElMessage.error(apiError(e, '恢复失败'));
  }
}

async function hardDelete(row: AssetItem) {
  try {
    await ElMessageBox.confirm(
      `确认彻底删除「${row.displayName}」？此操作不可恢复。`,
      '彻底删除',
      { type: 'warning' },
    );
    await hardDeleteAsset(row.id);
    ElMessage.success('已彻底删除');
    await load();
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return;
    ElMessage.error(apiError(e, '彻底删除失败'));
  }
}

async function runBulk(
  actionLabel: string,
  items: AssetItem[],
  runner: (row: AssetItem) => Promise<void>,
) {
  let ok = 0;
  const failures: string[] = [];
  for (const row of items) {
    try {
      await runner(row);
      ok += 1;
    } catch (e: unknown) {
      failures.push(`${row.displayName}: ${apiError(e, '失败')}`);
    }
  }
  await load();
  if (failures.length === 0) {
    ElMessage.success(`${actionLabel}完成：${ok} 项`);
    return;
  }
  const preview = failures.slice(0, 3).join('；');
  const more = failures.length > 3 ? `等共 ${failures.length} 项失败` : '';
  ElMessage.warning(
    `${actionLabel}：成功 ${ok}，失败 ${failures.length}。${preview}${more ? `（${more}）` : ''}`,
  );
}

async function restoreAll() {
  if (isEmpty.value || bulkWorking.value) return;
  const allCount = total.value;
  const fetchCap = Math.min(allCount, ASSET_MAX_PAGE_SIZE);
  try {
    await ElMessageBox.confirm(
      allCount > ASSET_MAX_PAGE_SIZE
        ? `确认恢复回收站中前 ${fetchCap} 项（共 ${allCount} 项，单次最多 ${ASSET_MAX_PAGE_SIZE}）？`
        : `确认恢复回收站中全部 ${allCount} 项素材？`,
      '全部恢复',
      { type: 'info' },
    );
  } catch {
    return;
  }
  bulkWorking.value = true;
  try {
    const snapshot = await fetchAllDeleted();
    await runBulk('全部恢复', snapshot, (row) => restoreAsset(row.id).then(() => undefined));
  } finally {
    bulkWorking.value = false;
  }
}

async function hardDeleteAll() {
  if (isEmpty.value || bulkWorking.value) return;
  const allCount = total.value;
  const fetchCap = Math.min(allCount, ASSET_MAX_PAGE_SIZE);
  try {
    await ElMessageBox.confirm(
      allCount > ASSET_MAX_PAGE_SIZE
        ? `确认彻底删除回收站中前 ${fetchCap} 项（共 ${allCount} 项，单次最多 ${ASSET_MAX_PAGE_SIZE}）？此操作不可恢复。`
        : `确认彻底删除回收站中全部 ${allCount} 项素材？此操作不可恢复。`,
      '全部彻底删除',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  bulkWorking.value = true;
  try {
    const snapshot = await fetchAllDeleted();
    await runBulk('全部彻底删除', snapshot, (row) => hardDeleteAsset(row.id));
  } finally {
    bulkWorking.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section class="recycle-page">
    <div class="header">
      <div>
        <p class="eyebrow">RECYCLE</p>
        <h2>回收站</h2>
      </div>
      <div class="header-actions">
        <el-button
          type="primary"
          :disabled="isEmpty"
          :loading="bulkWorking"
          @click="restoreAll"
        >
          全部恢复
        </el-button>
        <el-button
          type="danger"
          :disabled="isEmpty"
          :loading="bulkWorking"
          @click="hardDeleteAll"
        >
          全部彻底删除
        </el-button>
        <el-button :disabled="bulkWorking" @click="load">刷新</el-button>
      </div>
    </div>

    <el-table v-loading="loading || bulkWorking" :data="rows" stripe empty-text="回收站为空">
      <el-table-column label="预览" width="88">
        <template #default="{ row }">
          <img class="thumb" :src="assetContentUrl(row.id)" :alt="row.displayName" />
        </template>
      </el-table-column>
      <el-table-column prop="displayName" label="显示名" min-width="160" />
      <el-table-column prop="originalFilename" label="原始文件" min-width="180" show-overflow-tooltip />
      <el-table-column prop="deletedAt" label="删除时间" min-width="180" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="bulkWorking" @click="restore(row)">
            恢复
          </el-button>
          <el-button link type="danger" :disabled="bulkWorking" @click="hardDelete(row)">
            彻底删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination
        background
        layout="total, prev, pager, next, jumper"
        :page-size="pageSize"
        :current-page="page + 1"
        :total="total"
        @current-change="onPageChange"
      />
    </div>
  </section>
</template>

<style scoped>
.recycle-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}
.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.eyebrow {
  margin: 0;
  color: #7a8699;
  letter-spacing: 0.08em;
  font-size: 12px;
}
.header h2 {
  margin: 8px 0 0;
}
.thumb {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 8px;
  background: #f2f4f8;
}
.pager {
  display: flex;
  justify-content: flex-end;
}
</style>
