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
import { listCategories, type AssetCategoryItem } from '../../api/category';
import ImageLightbox from '../../components/ImageLightbox.vue';

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
/** Non-portrait linked assets kept on save so expression/costume links are not wiped. */
const otherLinkedAssetIds = ref<number[]>([]);
const selectedLibraryIds = ref<number[]>([]);
const fileInput = ref<HTMLInputElement | null>(null);
const portraitCategoryId = ref<number | null>(null);

const pickerVisible = ref(false);
const pickerCategoryId = ref<number | 'all'>('all');
const pickerKeyword = ref('');
const pickerAssets = ref<AssetItem[]>([]);
const pickerSelectedIds = ref<number[]>([]);
const pickerLoading = ref(false);
const categories = ref<AssetCategoryItem[]>([]);

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
const previewSrc = computed(() =>
  previewAsset.value ? assetContentUrl(previewAsset.value.id) : null,
);
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
  otherLinkedAssetIds.value = [];
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

async function ensureCategories() {
  if (!categories.value.length) {
    categories.value = await listCategories();
  }
  portraitCategoryId.value =
    categories.value.find((c) => c.code === 'portrait')?.id ?? null;
}

function splitPortraitAssets(all: AssetItem[]): { portraits: AssetItem[]; otherIds: number[] } {
  const pid = portraitCategoryId.value;
  if (pid == null) {
    return { portraits: all, otherIds: [] };
  }
  return {
    portraits: all.filter((a) => a.categoryId === pid),
    otherIds: all.filter((a) => a.categoryId !== pid).map((a) => a.id),
  };
}

async function loadLinkedAssets(characterId: number) {
  assetLoading.value = true;
  try {
    await ensureCategories();
    const all = await listCharacterAssets(characterId);
    const { portraits, otherIds } = splitPortraitAssets(all);
    linkedAssets.value = portraits;
    otherLinkedAssetIds.value = otherIds;
    selectedLibraryIds.value = portraits.map((a) => a.id);
  } catch {
    ElMessage.error('加载人物立绘失败');
  } finally {
    assetLoading.value = false;
  }
}

async function openAssetPicker() {
  pickerSelectedIds.value = [...selectedLibraryIds.value];
  await ensureCategories();
  pickerCategoryId.value = portraitCategoryId.value ?? 'all';
  pickerKeyword.value = '';
  pickerVisible.value = true;
  await loadPickerAssets();
}

async function loadPickerAssets() {
  pickerLoading.value = true;
  try {
    pickerAssets.value = await listAssets({
      status: 'NORMAL',
      categoryId: pickerCategoryId.value === 'all' ? undefined : pickerCategoryId.value,
      q: pickerKeyword.value.trim() || undefined,
    });
  } finally {
    pickerLoading.value = false;
  }
}

function togglePickerAsset(id: number) {
  const idx = pickerSelectedIds.value.indexOf(id);
  if (idx >= 0) {
    pickerSelectedIds.value = pickerSelectedIds.value.filter((x) => x !== id);
  } else {
    pickerSelectedIds.value = [...pickerSelectedIds.value, id];
  }
}

function isPickerSelected(id: number): boolean {
  return pickerSelectedIds.value.includes(id);
}

function confirmAssetPicker() {
  const pid = portraitCategoryId.value;
  const byId = new Map<number, AssetItem>();
  for (const a of linkedAssets.value) byId.set(a.id, a);
  for (const a of pickerAssets.value) byId.set(a.id, a);

  const nextIds: number[] = [];
  for (const id of pickerSelectedIds.value) {
    const item = byId.get(id);
    if (!item) {
      if (selectedLibraryIds.value.includes(id)) nextIds.push(id);
      continue;
    }
    if (pid == null || item.categoryId === pid) nextIds.push(id);
  }
  const skipped = pickerSelectedIds.value.length - nextIds.length;
  selectedLibraryIds.value = nextIds;
  linkedAssets.value = nextIds
    .map((id) => byId.get(id))
    .filter((a): a is AssetItem => a != null);
  pickerVisible.value = false;
  if (skipped > 0) {
    ElMessage.warning(`已忽略 ${skipped} 项非「人物立绘」素材`);
  }
}

function openCreate() {
  editing.value = false;
  editingId.value = null;
  resetForm();
  dialogVisible.value = true;
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
  if (row.id != null) {
    await loadLinkedAssets(row.id);
  }
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
      ElMessage.success('人物已创建，可继续指定或上传立绘');
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
    await ensureCategories();
    const all = await listCharacterAssets(row.id);
    const { portraits } = splitPortraitAssets(all);
    if (!portraits.length) {
      ElMessage.info('该人物暂无立绘，请先编辑并上传或指定「人物立绘」分类素材');
      return;
    }
    previewTitle.value = row.name;
    previewAssets.value = portraits;
    previewIndex.value = 0;
    previewVisible.value = true;
  } catch {
    ElMessage.error('加载立绘预览失败');
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
    ElMessage.warning('请先保存人物，再上传立绘');
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
    await uploadCharacterAssets(editingId.value, files);
    await loadLinkedAssets(editingId.value);
    ElMessage.success(`已上传并关联 ${files.length} 张立绘`);
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
    const merged = Array.from(
      new Set([...otherLinkedAssetIds.value, ...selectedLibraryIds.value]),
    );
    await replaceCharacterAssets(editingId.value, merged);
    await loadLinkedAssets(editingId.value);
    ElMessage.success('已更新人物立绘关联');
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存立绘关联失败');
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
          <strong class="asset-panel-title">人物立绘</strong>
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
              上传立绘
            </el-button>
            <el-button :disabled="!canManageAssets" @click="openAssetPicker">
              从素材库指定
            </el-button>
            <el-button
              type="primary"
              plain
              :disabled="!canManageAssets"
              :loading="assetLoading"
              @click="saveLinkedAssets"
            >
              保存立绘关联
            </el-button>
          </div>
        </div>
        <p v-if="!canManageAssets" class="hint">先点击下方「保存」创建人物后，即可上传或指定立绘。</p>
        <template v-else>
          <p class="hint">仅展示「人物立绘」分类素材；表情/服装等其它关联不会在此显示，保存时会保留。</p>
          <div v-loading="assetLoading" class="linked-thumbs">
            <div v-for="asset in linkedAssets" :key="asset.id" class="thumb-card">
              <img :src="assetContentUrl(asset.id)" :alt="asset.displayName" />
              <div class="thumb-meta">
                <span :title="asset.displayName">{{ asset.displayName }}</span>
                <el-button link type="danger" @click="unlinkAsset(asset.id)">移除</el-button>
              </div>
            </div>
            <p v-if="!linkedAssets.length" class="hint">暂无关联立绘</p>
          </div>
          <p v-if="selectedLibraryIds.length" class="hint pending-hint">
            待保存立绘关联 {{ selectedLibraryIds.length }} 项（确定挑选后请点「保存立绘关联」）
          </p>
        </template>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="saving" @click="submit">
          {{ editing ? '保存资料' : '保存并继续指定立绘' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="pickerVisible"
      title="指定人物立绘"
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
          @click="togglePickerAsset(asset.id)"
        >
          <img :src="assetContentUrl(asset.id)" :alt="asset.displayName" />
          <span class="picker-card-title" :title="asset.displayName">{{ asset.displayName }}</span>
        </button>
        <p v-if="!pickerLoading && !pickerAssets.length" class="hint picker-empty">暂无 NORMAL 素材</p>
      </div>

      <template #footer>
        <div class="picker-footer">
          <span class="picker-count">已选 {{ pickerSelectedIds.length }} 项</span>
          <div class="picker-footer-actions">
            <el-button @click="pickerVisible = false">取消</el-button>
            <el-button type="primary" @click="confirmAssetPicker">确定</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <ImageLightbox
      v-model="previewVisible"
      :src="previewSrc"
      :alt="previewAsset?.displayName"
    >
      <template #chrome>
        <span>{{ previewTitle }} · {{ previewAsset?.displayName }}</span>
        <span>{{ previewIndex + 1 }} / {{ previewAssets.length }}</span>
        <el-button size="small" @click="previewPrev">上一张</el-button>
        <el-button size="small" type="primary" @click="previewNext">下一张</el-button>
      </template>
    </ImageLightbox>

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
.pending-hint {
  margin-top: 8px;
}
.linked-thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  min-height: 48px;
  margin-bottom: 4px;
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
