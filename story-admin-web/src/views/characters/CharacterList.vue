<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { assetContentUrl, listAssets, type AssetItem } from '../../api/asset';
import {
  addCharacterForm,
  createCharacter,
  deleteCharacter,
  listCharacterAssets,
  listCharacters,
  replaceCharacterAssets,
  updateCharacter,
  uploadCharacterAssets,
  type CharacterItem,
} from '../../api/character';
import { listIdentities } from '../../api/characterIdentity';

const router = useRouter();
const loading = ref(false);
const rows = ref<CharacterItem[]>([]);
const identityNameById = ref(new Map<number, string>());
const dialogVisible = ref(false);
const editing = ref(false);
const editingId = ref<number | null>(null);
const saving = ref(false);
const assetLoading = ref(false);
const uploading = ref(false);
const linkedAssets = ref<AssetItem[]>([]);
const libraryAssets = ref<AssetItem[]>([]);
const selectedLibraryIds = ref<number[]>([]);
const fileInput = ref<HTMLInputElement | null>(null);

const previewVisible = ref(false);
const previewTitle = ref('');
const previewAssets = ref<AssetItem[]>([]);
const previewIndex = ref(0);

const formDialogVisible = ref(false);
const formSaving = ref(false);
const formSource = ref<CharacterItem | null>(null);
const formDialog = reactive({
  identityName: '',
  originalFormLabel: '默认',
  name: '',
  formLabel: '',
  alias: '',
  gender: '',
  ageStage: '',
  race: '',
  occupation: '',
  storyName: '',
  publicIntro: '',
  internalNote: '',
});

const filters = reactive({
  q: '',
  storyName: '',
  gender: '',
  ageStage: '',
  race: '',
  occupation: '',
});
const form = reactive({
  name: '',
  alias: '',
  gender: '',
  ageStage: '',
  race: '',
  occupation: '',
  storyName: '',
  heightCm: null as number | null,
  publicIntro: '',
  internalNote: '',
});

const previewAsset = computed(() => previewAssets.value[previewIndex.value] ?? null);
const canManageAssets = computed(() => editingId.value != null);
const formHasIdentity = computed(() => formSource.value?.identityId != null);

function apiError(e: unknown, fallback: string): string {
  const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
  return msg || fallback;
}

function formatIdentityCell(row: CharacterItem): string {
  if (row.identityId == null) return '';
  const identityName = identityNameById.value.get(row.identityId)?.trim() || '';
  const label = row.formLabel?.trim() || '';
  if (identityName && label) return `${identityName} · ${label}`;
  if (identityName) return identityName;
  if (label) return label;
  return `#${row.identityId}`;
}

function formatHeight(row: CharacterItem): string {
  return row.heightCm != null ? `${row.heightCm} cm` : '-';
}

function resetForm() {
  form.name = '';
  form.alias = '';
  form.gender = '';
  form.ageStage = '';
  form.race = '';
  form.occupation = '';
  form.storyName = '';
  form.heightCm = null;
  form.publicIntro = '';
  form.internalNote = '';
  linkedAssets.value = [];
  selectedLibraryIds.value = [];
}

function resetFilters() {
  filters.q = '';
  filters.storyName = '';
  filters.gender = '';
  filters.ageStage = '';
  filters.race = '';
  filters.occupation = '';
}

async function loadIdentities() {
  try {
    const list = await listIdentities();
    identityNameById.value = new Map(list.map((item) => [item.id, item.name]));
  } catch {
    identityNameById.value = new Map();
  }
}

async function load() {
  loading.value = true;
  try {
    const [chars] = await Promise.all([
      listCharacters({
        q: filters.q.trim() || undefined,
        storyName: filters.storyName.trim() || undefined,
        gender: filters.gender.trim() || undefined,
        ageStage: filters.ageStage.trim() || undefined,
        race: filters.race.trim() || undefined,
        occupation: filters.occupation.trim() || undefined,
      }),
      loadIdentities(),
    ]);
    rows.value = chars;
  } catch {
    ElMessage.error('加载人物失败');
  } finally {
    loading.value = false;
  }
}

async function loadLinkedAssets(characterId: number) {
  assetLoading.value = true;
  try {
    linkedAssets.value = await listCharacterAssets(characterId);
    selectedLibraryIds.value = linkedAssets.value.map((a) => a.id);
  } catch {
    ElMessage.error('加载人物素材失败');
  } finally {
    assetLoading.value = false;
  }
}

async function loadLibraryAssets() {
  try {
    libraryAssets.value = await listAssets({ status: 'NORMAL' });
  } catch {
    libraryAssets.value = [];
  }
}

function openCreate() {
  editing.value = false;
  editingId.value = null;
  resetForm();
  dialogVisible.value = true;
  void loadLibraryAssets();
}

async function openEdit(row: CharacterItem) {
  editing.value = true;
  editingId.value = row.id ?? null;
  form.name = row.name ?? '';
  form.alias = row.alias ?? '';
  form.gender = row.gender ?? '';
  form.ageStage = row.ageStage ?? '';
  form.race = row.race ?? '';
  form.occupation = row.occupation ?? '';
  form.storyName = row.storyName ?? '';
  form.heightCm = row.heightCm ?? null;
  form.publicIntro = row.publicIntro ?? '';
  form.internalNote = row.internalNote ?? '';
  dialogVisible.value = true;
  await Promise.all([
    loadLibraryAssets(),
    row.id != null ? loadLinkedAssets(row.id) : Promise.resolve(),
  ]);
}

function payload() {
  return {
    name: form.name.trim(),
    alias: form.alias.trim() || null,
    gender: form.gender.trim() || null,
    ageStage: form.ageStage.trim() || null,
    race: form.race.trim() || null,
    occupation: form.occupation.trim() || null,
    storyName: form.storyName.trim() || null,
    heightCm: form.heightCm,
    publicIntro: form.publicIntro.trim() || null,
    internalNote: form.internalNote.trim() || null,
  };
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写姓名');
    return;
  }
  saving.value = true;
  try {
    if (editing.value && editingId.value != null) {
      await updateCharacter(editingId.value, payload());
      ElMessage.success('已更新');
      dialogVisible.value = false;
      await load();
    } else {
      const created = await createCharacter(payload());
      editing.value = true;
      editingId.value = created.id ?? null;
      ElMessage.success('人物已创建，可继续指定或上传预览图');
      if (created.id != null) {
        await loadLinkedAssets(created.id);
      }
      await load();
    }
  } catch (e: any) {
    const msg = e?.response?.data?.message || '保存失败';
    ElMessage.error(msg);
  } finally {
    saving.value = false;
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

function onMoreCommand(command: string, row: CharacterItem) {
  if (command === 'addForm') {
    openAddForm(row);
  } else if (command === 'delete') {
    void remove(row);
  }
}

async function openPreview(row: CharacterItem) {
  if (row.id == null) return;
  try {
    const assets = await listCharacterAssets(row.id);
    if (!assets.length) {
      ElMessage.info('该人物暂无预览图，请先编辑并上传或指定素材');
      return;
    }
    previewTitle.value = row.name;
    previewAssets.value = assets;
    previewIndex.value = 0;
    previewVisible.value = true;
  } catch {
    ElMessage.error('加载预览失败');
  }
}

function previewNext() {
  if (!previewAssets.value.length) return;
  previewIndex.value = (previewIndex.value + 1) % previewAssets.value.length;
}

function previewPrev() {
  if (!previewAssets.value.length) return;
  previewIndex.value =
    (previewIndex.value - 1 + previewAssets.value.length) % previewAssets.value.length;
}

function triggerUpload() {
  if (!canManageAssets.value) {
    ElMessage.warning('请先保存人物，再上传预览图');
    return;
  }
  fileInput.value?.click();
}

async function onFilesPicked(ev: Event) {
  const input = ev.target as HTMLInputElement;
  const files = input.files ? Array.from(input.files) : [];
  input.value = '';
  if (!files.length || editingId.value == null) return;
  uploading.value = true;
  try {
    linkedAssets.value = await uploadCharacterAssets(editingId.value, files);
    selectedLibraryIds.value = linkedAssets.value.map((a) => a.id);
    ElMessage.success(`已上传并关联 ${files.length} 张预览图`);
    await loadLibraryAssets();
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '上传失败');
  } finally {
    uploading.value = false;
  }
}

async function saveLinkedAssets() {
  if (editingId.value == null) return;
  assetLoading.value = true;
  try {
    linkedAssets.value = await replaceCharacterAssets(editingId.value, selectedLibraryIds.value);
    ElMessage.success('已更新人物素材关联');
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存素材关联失败');
  } finally {
    assetLoading.value = false;
  }
}

function unlinkAsset(assetId: number) {
  selectedLibraryIds.value = selectedLibraryIds.value.filter((id) => id !== assetId);
  linkedAssets.value = linkedAssets.value.filter((a) => a.id !== assetId);
}

function resetFormDialog() {
  formDialog.identityName = '';
  formDialog.originalFormLabel = '默认';
  formDialog.name = '';
  formDialog.formLabel = '';
  formDialog.alias = '';
  formDialog.gender = '';
  formDialog.ageStage = '';
  formDialog.race = '';
  formDialog.occupation = '';
  formDialog.storyName = '';
  formDialog.publicIntro = '';
  formDialog.internalNote = '';
}

function openAddForm(row: CharacterItem) {
  if (row.id == null) return;
  formSource.value = row;
  resetFormDialog();
  formDialog.identityName =
    row.identityId != null
      ? identityNameById.value.get(row.identityId) ?? ''
      : row.name ?? '';
  formDialog.originalFormLabel = row.formLabel?.trim() || '默认';
  formDialog.storyName = row.storyName ?? '';
  formDialogVisible.value = true;
}

async function submitAddForm() {
  const source = formSource.value;
  if (source?.id == null) return;
  if (!formHasIdentity.value && !formDialog.identityName.trim()) {
    ElMessage.warning('请填写本体名称');
    return;
  }
  if (!formDialog.name.trim()) {
    ElMessage.warning('请填写新形态姓名');
    return;
  }
  formSaving.value = true;
  try {
    const detail = await addCharacterForm(source.id, {
      identityName: formHasIdentity.value
        ? null
        : formDialog.identityName.trim() || null,
      originalFormLabel: formDialog.originalFormLabel.trim() || null,
      newCharacter: {
        name: formDialog.name.trim(),
        formLabel: formDialog.formLabel.trim() || null,
        alias: formDialog.alias.trim() || null,
        gender: formDialog.gender.trim() || null,
        ageStage: formDialog.ageStage.trim() || null,
        race: formDialog.race.trim() || null,
        occupation: formDialog.occupation.trim() || null,
        storyName: formDialog.storyName.trim() || null,
        publicIntro: formDialog.publicIntro.trim() || null,
        internalNote: formDialog.internalNote.trim() || null,
      },
    });
    ElMessage.success('已添加形态');
    formDialogVisible.value = false;
    await router.push(`/character-identities/${detail.id}`);
  } catch (e: unknown) {
    ElMessage.error(apiError(e, '添加形态失败'));
  } finally {
    formSaving.value = false;
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

    <el-form class="filters" :inline="true" @submit.prevent="load">
      <el-form-item label="关键词">
        <el-input
          v-model="filters.q"
          clearable
          placeholder="姓名 / 别名 / 编号 / 职业"
          class="filter-control filter-control--wide"
        />
      </el-form-item>
      <el-form-item label="故事">
        <el-input
          v-model="filters.storyName"
          clearable
          placeholder="所属故事/系列"
          class="filter-control"
        />
      </el-form-item>
      <el-form-item label="性别">
        <el-input
          v-model="filters.gender"
          clearable
          placeholder="如 女"
          class="filter-control filter-control--sm"
        />
      </el-form-item>
      <el-form-item label="年龄/阶段">
        <el-input
          v-model="filters.ageStage"
          clearable
          placeholder="如 青年"
          class="filter-control filter-control--md"
        />
      </el-form-item>
      <el-form-item label="种族">
        <el-input
          v-model="filters.race"
          clearable
          placeholder="如 人类"
          class="filter-control filter-control--md"
        />
      </el-form-item>
      <el-form-item label="身份/职业">
        <el-input
          v-model="filters.occupation"
          clearable
          placeholder="如 怪盗"
          class="filter-control"
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

    <el-table v-loading="loading" :data="rows" stripe empty-text="暂无人物">
      <el-table-column prop="code" label="编号" width="100" />
      <el-table-column prop="name" label="姓名" min-width="100" />
      <el-table-column prop="alias" label="别名" min-width="90" show-overflow-tooltip />
      <el-table-column prop="storyName" label="所属故事" min-width="110" show-overflow-tooltip />
      <el-table-column label="身高" width="88">
        <template #default="{ row }">
          {{ formatHeight(row) }}
        </template>
      </el-table-column>
      <el-table-column prop="gender" label="性别" width="64" />
      <el-table-column prop="ageStage" label="年龄/阶段" width="96" />
      <el-table-column prop="race" label="种族" width="80" />
      <el-table-column prop="occupation" label="身份/职业" min-width="100" show-overflow-tooltip />
      <el-table-column label="所属本体" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <router-link
            v-if="row.identityId != null"
            class="identity-link"
            :to="`/character-identities/${row.identityId}`"
          >
            {{ formatIdentityCell(row) }}
          </router-link>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="168" fixed="right">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button link type="primary" @click="openPreview(row)">预览</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-dropdown trigger="click" @command="(cmd: string) => onMoreCommand(cmd, row)">
              <el-button link type="primary">
                更多
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="addForm">
                    {{ row.identityId != null ? '再添加形态' : '添加形态' }}
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided>
                    删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑人物' : '新增人物'"
      width="840px"
      destroy-on-close
      class="character-edit-dialog"
    >
      <el-form label-width="100px" class="edit-form">
        <div class="form-grid">
          <el-form-item label="姓名" required>
            <el-input v-model="form.name" placeholder="如 女怪盗" />
          </el-form-item>
          <el-form-item label="别名">
            <el-input v-model="form.alias" placeholder="可选" />
          </el-form-item>
          <el-form-item label="所属故事">
            <el-input v-model="form.storyName" placeholder="如 暗夜物语" />
          </el-form-item>
          <el-form-item label="身高 (cm)">
            <el-input-number
              v-model="form.heightCm"
              :min="1"
              :max="300"
              :step="1"
              controls-position="right"
              placeholder="可选"
              class="height-input"
            />
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
        </div>
        <el-form-item label="公开简介">
          <el-input v-model="form.publicIntro" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="内部说明">
          <el-input v-model="form.internalNote" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>

      <div class="asset-panel">
        <div class="asset-panel-head">
          <strong class="asset-panel-title">人物预览素材</strong>
          <div class="asset-panel-actions">
            <input
              ref="fileInput"
              type="file"
              accept=".jpg,.jpeg,.png,.webp,.gif,image/*"
              multiple
              hidden
              @change="onFilesPicked"
            />
            <el-button :disabled="!canManageAssets" :loading="uploading" @click="triggerUpload">
              上传预览图
            </el-button>
            <el-button
              type="primary"
              plain
              :disabled="!canManageAssets"
              :loading="assetLoading"
              @click="saveLinkedAssets"
            >
              保存关联
            </el-button>
          </div>
        </div>
        <p v-if="!canManageAssets" class="hint">先点击下方「保存」创建人物后，即可上传或指定素材。</p>
        <template v-else>
          <div v-loading="assetLoading" class="linked-thumbs">
            <div v-for="asset in linkedAssets" :key="asset.id" class="thumb-card">
              <img :src="assetContentUrl(asset.id)" :alt="asset.displayName" />
              <div class="thumb-meta">
                <span :title="asset.displayName">{{ asset.displayName }}</span>
                <el-button link type="danger" @click="unlinkAsset(asset.id)">移除</el-button>
              </div>
            </div>
            <p v-if="!linkedAssets.length" class="hint">暂无关联素材</p>
          </div>
          <el-form-item label="从素材库指定" label-width="110px" class="library-pick">
            <el-select
              v-model="selectedLibraryIds"
              multiple
              filterable
              clearable
              collapse-tags
              collapse-tags-tooltip
              placeholder="选择已有素材"
              class="library-select"
            >
              <el-option
                v-for="asset in libraryAssets"
                :key="asset.id"
                :label="`${asset.displayName} (#${asset.id})`"
                :value="asset.id"
              />
            </el-select>
          </el-form-item>
        </template>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="saving" @click="submit">
          {{ editing ? '保存资料' : '保存并继续指定素材' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="previewVisible"
      :title="`预览 · ${previewTitle}`"
      width="640px"
      align-center
      destroy-on-close
    >
      <div v-if="previewAsset" class="preview-box">
        <img :src="assetContentUrl(previewAsset.id)" :alt="previewAsset.displayName" />
        <div class="preview-caption">
          <span>{{ previewAsset.displayName }}</span>
          <span>{{ previewIndex + 1 }} / {{ previewAssets.length }}</span>
        </div>
        <div class="preview-nav">
          <el-button @click="previewPrev">上一张</el-button>
          <el-button type="primary" @click="previewNext">下一张</el-button>
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="formDialogVisible"
      :title="formHasIdentity ? '再添加形态' : '添加形态'"
      width="640px"
      destroy-on-close
    >
      <p v-if="formSource" class="hint form-source">
        原人物：{{ formSource.name }}
        <span v-if="formSource.code">({{ formSource.code }})</span>
      </p>
      <el-form label-width="120px">
        <el-form-item v-if="!formHasIdentity" label="本体名称" required>
          <el-input v-model="formDialog.identityName" placeholder="新建本体时使用的名称" />
        </el-form-item>
        <el-form-item v-if="!formHasIdentity" label="原形态标签">
          <el-input v-model="formDialog.originalFormLabel" placeholder="默认「默认」" />
        </el-form-item>
        <el-divider content-position="left">新形态人物</el-divider>
        <el-form-item label="姓名" required>
          <el-input v-model="formDialog.name" placeholder="新形态姓名" />
        </el-form-item>
        <el-form-item label="形态标签">
          <el-input v-model="formDialog.formLabel" placeholder="如 怪盗" />
        </el-form-item>
        <el-form-item label="别名">
          <el-input v-model="formDialog.alias" placeholder="可选" />
        </el-form-item>
        <el-form-item label="所属故事">
          <el-input v-model="formDialog.storyName" placeholder="可选" />
        </el-form-item>
        <el-form-item label="性别">
          <el-input v-model="formDialog.gender" placeholder="可选" />
        </el-form-item>
        <el-form-item label="年龄/阶段">
          <el-input v-model="formDialog.ageStage" placeholder="可选" />
        </el-form-item>
        <el-form-item label="种族">
          <el-input v-model="formDialog.race" placeholder="可选" />
        </el-form-item>
        <el-form-item label="身份/职业">
          <el-input v-model="formDialog.occupation" placeholder="可选" />
        </el-form-item>
        <el-form-item label="公开简介">
          <el-input v-model="formDialog.publicIntro" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="内部说明">
          <el-input v-model="formDialog.internalNote" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formSaving" @click="submitAddForm">确定</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.character-page {
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
.filter-control--md {
  width: 120px;
  min-width: 120px;
}
.filter-control--sm {
  width: 96px;
  min-width: 96px;
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
.edit-form .form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 16px;
}
.edit-form .form-grid :deep(.el-form-item) {
  margin-bottom: 16px;
}
.height-input {
  width: 100%;
}
.asset-panel {
  margin-top: 8px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f7f9fc;
}
.asset-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.asset-panel-title {
  flex-shrink: 0;
}
.asset-panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
.hint {
  margin: 0;
  color: #6f7e9d;
  font-size: 13px;
}
.linked-thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  min-height: 48px;
  margin-bottom: 12px;
}
.thumb-card {
  width: 104px;
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 4px 14px #24325214;
}
.thumb-card img {
  width: 104px;
  height: 104px;
  object-fit: cover;
  display: block;
  background: #eef1f7;
}
.thumb-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 8px;
  font-size: 12px;
}
.thumb-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.library-pick {
  margin-bottom: 0;
}
.library-select {
  width: 100%;
}
.preview-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.preview-box img {
  max-width: 100%;
  max-height: 420px;
  object-fit: contain;
  border-radius: 12px;
  background: #f4f6fa;
}
.preview-caption {
  display: flex;
  gap: 16px;
  color: #6f7e9d;
}
.preview-nav {
  display: flex;
  gap: 10px;
}
.identity-link {
  color: #3a6ff0;
  text-decoration: none;
}
.identity-link:hover {
  text-decoration: underline;
}
.form-source {
  margin: 0 0 12px;
}
</style>
