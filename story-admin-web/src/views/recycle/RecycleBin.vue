<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  assetContentUrl,
  hardDeleteAsset,
  listAssets,
  restoreAsset,
  type AssetItem,
} from '../../api/asset';

const loading = ref(false);
const rows = ref<AssetItem[]>([]);

function apiError(e: unknown, fallback: string): string {
  const err = e as { response?: { data?: { message?: string } } };
  return err?.response?.data?.message || fallback;
}

async function load() {
  loading.value = true;
  try {
    rows.value = await listAssets({ status: 'DELETED' });
  } catch (e) {
    ElMessage.error(apiError(e, '加载回收站失败'));
  } finally {
    loading.value = false;
  }
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

onMounted(load);
</script>

<template>
  <section class="recycle-page">
    <div class="header">
      <div>
        <p class="eyebrow">RECYCLE</p>
        <h2>回收站</h2>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe empty-text="回收站为空">
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
          <el-button link type="primary" @click="restore(row)">恢复</el-button>
          <el-button link type="danger" @click="hardDelete(row)">彻底删除</el-button>
        </template>
      </el-table-column>
    </el-table>
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
</style>
