<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  createCharacter,
  deleteCharacter,
  listCharacters,
  updateCharacter,
  type CharacterItem,
} from '../../api/character';

const loading = ref(false);
const rows = ref<CharacterItem[]>([]);
const dialogVisible = ref(false);
const editing = ref(false);
const editingId = ref<number | null>(null);
const form = reactive({
  name: '',
  alias: '',
  gender: '',
  ageStage: '',
  race: '',
  occupation: '',
  publicIntro: '',
  internalNote: '',
});

function resetForm() {
  form.name = '';
  form.alias = '';
  form.gender = '';
  form.ageStage = '';
  form.race = '';
  form.occupation = '';
  form.publicIntro = '';
  form.internalNote = '';
}

async function load() {
  loading.value = true;
  try {
    rows.value = await listCharacters();
  } catch {
    ElMessage.error('加载人物失败');
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

function openEdit(row: CharacterItem) {
  editing.value = true;
  editingId.value = row.id ?? null;
  form.name = row.name ?? '';
  form.alias = row.alias ?? '';
  form.gender = row.gender ?? '';
  form.ageStage = row.ageStage ?? '';
  form.race = row.race ?? '';
  form.occupation = row.occupation ?? '';
  form.publicIntro = row.publicIntro ?? '';
  form.internalNote = row.internalNote ?? '';
  dialogVisible.value = true;
}

function payload() {
  return {
    name: form.name.trim(),
    alias: form.alias.trim() || null,
    gender: form.gender.trim() || null,
    ageStage: form.ageStage.trim() || null,
    race: form.race.trim() || null,
    occupation: form.occupation.trim() || null,
    publicIntro: form.publicIntro.trim() || null,
    internalNote: form.internalNote.trim() || null,
  };
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写姓名');
    return;
  }
  try {
    if (editing.value && editingId.value != null) {
      await updateCharacter(editingId.value, payload());
    } else {
      await createCharacter(payload());
    }
    ElMessage.success(editing.value ? '已更新' : '已新增');
    dialogVisible.value = false;
    await load();
  } catch (e: any) {
    const msg = e?.response?.data?.message || '保存失败';
    ElMessage.error(msg);
  }
}

async function remove(row: CharacterItem) {
  if (row.id == null) return;
  try {
    await ElMessageBox.confirm(`确认删除人物「${row.name}」？`, '删除确认', { type: 'warning' });
    await deleteCharacter(row.id);
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
  <section class="character-page">
    <div class="header">
      <div>
        <p class="eyebrow">CHARACTERS</p>
        <h2>人物管理</h2>
      </div>
      <el-button type="primary" @click="openCreate">新增人物</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe empty-text="暂无人物">
      <el-table-column prop="code" label="编号" width="120" />
      <el-table-column prop="name" label="姓名" min-width="120" />
      <el-table-column prop="alias" label="别名" min-width="120" show-overflow-tooltip />
      <el-table-column prop="gender" label="性别" width="80" />
      <el-table-column prop="ageStage" label="年龄/阶段" width="110" />
      <el-table-column prop="race" label="种族" width="100" />
      <el-table-column prop="occupation" label="身份/职业" min-width="120" show-overflow-tooltip />
      <el-table-column prop="publicIntro" label="公开简介" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑人物' : '新增人物'" width="560px">
      <el-form label-width="96px">
        <el-form-item label="姓名" required>
          <el-input v-model="form.name" placeholder="如 女怪盗" />
        </el-form-item>
        <el-form-item label="别名">
          <el-input v-model="form.alias" placeholder="可选" />
        </el-form-item>
        <el-form-item label="性别">
          <el-input v-model="form.gender" placeholder="如 女" />
        </el-form-item>
        <el-form-item label="年龄/阶段">
          <el-input v-model="form.ageStage" placeholder="如 青年" />
        </el-form-item>
        <el-form-item label="种族">
          <el-input v-model="form.race" placeholder="如 人类" />
        </el-form-item>
        <el-form-item label="身份/职业">
          <el-input v-model="form.occupation" placeholder="如 怪盗" />
        </el-form-item>
        <el-form-item label="公开简介">
          <el-input v-model="form.publicIntro" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="内部说明">
          <el-input v-model="form.internalNote" type="textarea" :rows="3" />
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
.character-page {
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
