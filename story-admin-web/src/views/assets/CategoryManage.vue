<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  createCategory,
  deleteCategory,
  listCategories,
  updateCategory,
  type AssetCategoryItem,
} from '../../api/category';

const loading = ref(false);
const rows = ref<AssetCategoryItem[]>([]);
const dialogVisible = ref(false);
const editing = ref(false);
const form = reactive({
  id: null as number | null,
  code: '',
  name: '',
  sortOrder: 0,
});

function apiError(e: unknown, fallback: string): string {
  const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
  return msg || fallback;
}

async function load() {
  loading.value = true;
  try {
    rows.value = await listCategories();
  } catch (e) {
    ElMessage.error(apiError(e, '加载分类失败'));
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editing.value = false;
  form.id = null;
  form.code = '';
  form.name = '';
  form.sortOrder = (rows.value[rows.value.length - 1]?.sortOrder ?? 0) + 1;
  dialogVisible.value = true;
}

function openEdit(row: AssetCategoryItem) {
  editing.value = true;
  form.id = row.id;
  form.code = row.code;
  form.name = row.name;
  form.sortOrder = row.sortOrder;
  dialogVisible.value = true;
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写分类名称');
    return;
  }
  if (!editing.value && !form.code.trim()) {
    ElMessage.warning('请填写分类编码');
    return;
  }
  try {
    if (editing.value && form.id != null) {
      await updateCategory(form.id, {
        name: form.name.trim(),
        sortOrder: form.sortOrder,
      });
      ElMessage.success('分类已更新');
    } else {
      await createCategory({
        code: form.code.trim(),
        name: form.name.trim(),
        sortOrder: form.sortOrder,
      });
      ElMessage.success('分类已新增');
    }
    dialogVisible.value = false;
    await load();
  } catch (e) {
    ElMessage.error(apiError(e, '保存分类失败'));
  }
}

async function remove(row: AssetCategoryItem) {
  if (row.systemPreset) {
    ElMessage.warning('预置分类不可删除');
    return;
  }
  try {
    await ElMessageBox.confirm(`确认删除分类「${row.name}」？`, '删除确认', { type: 'warning' });
    await deleteCategory(row.id);
    ElMessage.success('已删除分类');
    await load();
  } catch (e) {
    if (e === 'cancel' || e === 'close') return;
    ElMessage.error(apiError(e, '删除分类失败'));
  }
}

onMounted(load);
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">ASSET CATEGORIES</p>
        <h2>素材分类配置</h2>
        <p class="hint">在此维护工作台左侧的大栏目；预置分类不可删除，可改显示名与排序。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增分类</el-button>
    </header>

    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column prop="code" label="编码" width="140" />
      <el-table-column prop="name" label="显示名称" min-width="160" />
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          <el-tag :type="row.systemPreset ? 'info' : 'success'" size="small">
            {{ row.systemPreset ? '系统预置' : '自定义' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" :disabled="row.systemPreset" @click="remove(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑分类' : '新增分类'"
      width="440px"
      destroy-on-close
    >
      <el-form label-width="88px">
        <el-form-item label="编码" required>
          <el-input
            v-model="form.code"
            :disabled="editing"
            placeholder="如 location（创建后不可改）"
          />
        </el-form-item>
        <el-form-item label="显示名称" required>
          <el-input v-model="form.name" placeholder="如 地点" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
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
.page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.eyebrow {
  margin: 0;
  color: #6f7e9d;
  letter-spacing: 0.15em;
  font-size: 12px;
}
h2 {
  margin: 6px 0 8px;
}
.hint {
  margin: 0;
  color: #6f7e9d;
  font-size: 14px;
}
</style>
