<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listIdentities, removeIdentity, type IdentityDetail } from '../../api/characterIdentity';

const router = useRouter();
const loading = ref(false);
const rows = ref<IdentityDetail[]>([]);

function apiError(e: unknown, fallback: string): string {
  const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
  return msg || fallback;
}

async function load() {
  loading.value = true;
  try {
    rows.value = await listIdentities();
  } catch (e) {
    ElMessage.error(apiError(e, '加载人物本体失败'));
  } finally {
    loading.value = false;
  }
}

function goCreate() {
  void router.push({ name: 'character-identity-edit', params: { id: 'new' } });
}

function goEdit(row: IdentityDetail) {
  void router.push({ name: 'character-identity-edit', params: { id: String(row.id) } });
}

async function remove(row: IdentityDetail) {
  try {
    await ElMessageBox.confirm(`确认删除人物本体「${row.name}」？`, '删除确认', {
      type: 'warning',
    });
    await removeIdentity(row.id);
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
  <section class="identity-list-page">
    <div class="header">
      <div>
        <p class="eyebrow">CHARACTERS</p>
        <h2>人物本体</h2>
      </div>
      <div class="actions">
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="goCreate">新建</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="rows" stripe empty-text="暂无人物本体">
      <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="code" label="编号" width="120" />
      <el-table-column prop="storyName" label="故事" min-width="140" show-overflow-tooltip />
      <el-table-column label="形态数" width="100">
        <template #default="{ row }">
          {{ row.memberCount ?? row.members?.length ?? 0 }}
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
.identity-list-page {
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
