<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listCombos, removeCombo, type ComboDetail } from '../../api/combo';

const router = useRouter();
const loading = ref(false);
const rows = ref<ComboDetail[]>([]);

function apiError(e: unknown, fallback: string): string {
  const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
  return msg || fallback;
}

async function load() {
  loading.value = true;
  try {
    rows.value = await listCombos();
  } catch (e) {
    ElMessage.error(apiError(e, '加载组合失败'));
  } finally {
    loading.value = false;
  }
}

function goCreate() {
  void router.push({ name: 'asset-combo-edit', params: { id: 'new' } });
}

function goEdit(row: ComboDetail) {
  void router.push({ name: 'asset-combo-edit', params: { id: String(row.id) } });
}

async function remove(row: ComboDetail) {
  try {
    await ElMessageBox.confirm(`确认删除组合「${row.name}」？`, '删除确认', { type: 'warning' });
    await removeCombo(row.id);
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
  <section class="combo-list-page">
    <div class="header">
      <div>
        <p class="eyebrow">ASSETS</p>
        <h2>组合编排</h2>
      </div>
      <div class="actions">
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="goCreate">新建</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="rows" stripe empty-text="暂无组合">
      <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="成员数" width="100">
        <template #default="{ row }">
          {{ row.members?.length ?? 0 }}
        </template>
      </el-table-column>
      <el-table-column label="默认间隔" width="120">
        <template #default="{ row }">
          {{ row.defaultIntervalSec }} 秒
        </template>
      </el-table-column>
      <el-table-column label="是否循环" width="100">
        <template #default="{ row }">
          {{ row.loopEnabled ? '是' : '否' }}
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<style scoped>
.combo-list-page {
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
.eyebrow {
  margin: 0;
  color: #7a8699;
  letter-spacing: 0.08em;
  font-size: 12px;
}
.header h2 {
  margin: 8px 0 0;
}
.actions {
  display: flex;
  gap: 8px;
}
</style>
