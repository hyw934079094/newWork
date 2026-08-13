<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { createConfig, deleteConfig, listConfigs, upsertConfig, type SysConfigItem } from '../../api/config';

const loading = ref(false);
const rows = ref<SysConfigItem[]>([]);
const dialogVisible = ref(false);
const editing = ref(false);
const form = reactive({
  key: '',
  value: '',
  remark: '',
});

async function load() {
  loading.value = true;
  try {
    rows.value = await listConfigs();
  } catch (e) {
    ElMessage.error('加载配置失败');
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editing.value = false;
  form.key = '';
  form.value = '';
  form.remark = '';
  dialogVisible.value = true;
}

function openEdit(row: SysConfigItem) {
  editing.value = true;
  form.key = row.configKey;
  form.value = row.configValue ?? '';
  form.remark = row.remark ?? '';
  dialogVisible.value = true;
}

async function submit() {
  if (!form.key.trim()) {
    ElMessage.warning('请填写配置键');
    return;
  }
  try {
    if (editing.value) {
      await upsertConfig(form.key.trim(), { value: form.value, remark: form.remark });
    } else {
      await createConfig(form.key.trim(), form.value, form.remark);
    }
    ElMessage.success(editing.value ? '已更新' : '已新增');
    dialogVisible.value = false;
    await load();
  } catch (e: any) {
    const msg = e?.response?.data?.message || '保存失败';
    ElMessage.error(msg);
  }
}

async function remove(row: SysConfigItem) {
  try {
    await ElMessageBox.confirm(`确认删除配置「${row.configKey}」？`, '删除确认', { type: 'warning' });
    await deleteConfig(row.configKey);
    ElMessage.success('已删除');
    await load();
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return;
    const msg = e?.response?.data?.message || '删除失败';
    ElMessage.error(msg);
  }
}

onMounted(load);
</script>

<template>
  <section class="config-page">
    <div class="header">
      <div>
        <p class="eyebrow">SYSTEM</p>
        <h2>系统配置</h2>
      </div>
      <el-button type="primary" @click="openCreate">新增配置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe empty-text="暂无配置">
      <el-table-column prop="configKey" label="Key" min-width="180" />
      <el-table-column prop="configValue" label="Value" min-width="240" show-overflow-tooltip />
      <el-table-column prop="remark" label="Remark" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑配置' : '新增配置'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="Key">
          <el-input v-model="form.key" :disabled="editing" placeholder="如 storage.root" />
        </el-form-item>
        <el-form-item label="Value">
          <el-input v-model="form.value" type="textarea" :rows="3" placeholder="配置值" />
        </el-form-item>
        <el-form-item label="Remark">
          <el-input v-model="form.remark" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.config-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}
.header h2 {
  margin: 8px 0 0;
}
</style>
