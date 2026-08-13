<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  createIdentity,
  getIdentity,
  setIdentityAssets,
  setIdentityMembers,
  updateIdentity,
  type IdentityUpsertPayload,
} from '../../api/characterIdentity';
import { listCharacters, type CharacterItem } from '../../api/character';
import { listAssets, type AssetItem } from '../../api/asset';

/** Backend already returns identity fields; Task 5 will formalize on CharacterItem. */
type CharacterRow = CharacterItem & {
  identityId?: number | null;
  formLabel?: string | null;
};

interface MemberDraft {
  characterId: number;
  code: string;
  name: string;
  formLabel: string;
  assetCount: number;
}

const route = useRoute();
const router = useRouter();

const identityId = computed(() => String(route.params.id ?? ''));
const isNew = computed(() => identityId.value === 'new');

const loading = ref(false);
const saving = ref(false);

const code = ref('');
const name = ref('');
const storyName = ref('');
const publicIntro = ref('');
const internalNote = ref('');

const members = ref<MemberDraft[]>([]);
const selectedAssetIds = ref<number[]>([]);

const allCharacters = ref<CharacterRow[]>([]);
const charactersLoading = ref(false);
const characterQuery = ref('');

const availableAssets = ref<AssetItem[]>([]);
const assetsLoading = ref(false);
const assetQuery = ref('');
const assetTableRef = ref<{
  clearSelection: () => void;
  toggleRowSelection: (row: AssetItem, selected?: boolean) => void;
} | null>(null);
const syncingAssetSelection = ref(false);

const memberIds = computed(() => new Set(members.value.map((m) => m.characterId)));

const pickerCharacters = computed(() => {
  const q = characterQuery.value.trim().toLowerCase();
  const currentId = isNew.value ? null : Number(identityId.value);
  return allCharacters.value.filter((c) => {
    if (c.id == null || memberIds.value.has(c.id)) return false;
    if (c.identityId != null && c.identityId !== currentId) return false;
    if (!q) return true;
    const hay = `${c.name} ${c.code ?? ''} ${c.alias ?? ''}`.toLowerCase();
    return hay.includes(q);
  });
});

function apiError(e: unknown, fallback: string): string {
  const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
  return msg || fallback;
}

function backToList() {
  void router.push({ name: 'character-identities' });
}

function resetForm() {
  code.value = '';
  name.value = '';
  storyName.value = '';
  publicIntro.value = '';
  internalNote.value = '';
  members.value = [];
  selectedAssetIds.value = [];
}

function addMember(row: CharacterRow) {
  if (row.id == null || memberIds.value.has(row.id)) return;
  members.value.push({
    characterId: row.id,
    code: row.code ?? '',
    name: row.name,
    formLabel: row.formLabel ?? '',
    assetCount: 0,
  });
}

function removeMember(index: number) {
  members.value.splice(index, 1);
}

function moveMember(index: number, delta: number) {
  const target = index + delta;
  if (target < 0 || target >= members.value.length) return;
  const copy = members.value.slice();
  const [item] = copy.splice(index, 1);
  copy.splice(target, 0, item);
  members.value = copy;
}

async function loadCharacters() {
  charactersLoading.value = true;
  try {
    allCharacters.value = (await listCharacters()) as CharacterRow[];
  } catch (e) {
    ElMessage.error(apiError(e, '加载人物失败'));
  } finally {
    charactersLoading.value = false;
  }
}

async function syncAssetTableSelection() {
  await nextTick();
  const table = assetTableRef.value;
  if (!table) return;
  syncingAssetSelection.value = true;
  try {
    table.clearSelection();
    const selected = new Set(selectedAssetIds.value);
    for (const row of availableAssets.value) {
      if (selected.has(row.id)) {
        table.toggleRowSelection(row, true);
      }
    }
  } finally {
    syncingAssetSelection.value = false;
  }
}

function onAssetSelectionChange(rows: AssetItem[]) {
  if (syncingAssetSelection.value) return;
  // Keep selections outside the current result page/filter.
  const visibleIds = new Set(availableAssets.value.map((a) => a.id));
  const kept = selectedAssetIds.value.filter((id) => !visibleIds.has(id));
  selectedAssetIds.value = [...kept, ...rows.map((r) => r.id)];
}

async function loadAvailableAssets() {
  assetsLoading.value = true;
  try {
    availableAssets.value = await listAssets({
      status: 'NORMAL',
      q: assetQuery.value.trim() || undefined,
    });
    await syncAssetTableSelection();
  } catch (e) {
    ElMessage.error(apiError(e, '加载素材失败'));
  } finally {
    assetsLoading.value = false;
  }
}

async function loadIdentity() {
  if (isNew.value) {
    resetForm();
    return;
  }
  const id = Number(identityId.value);
  if (!Number.isFinite(id)) {
    ElMessage.error('无效的本体 ID');
    backToList();
    return;
  }
  loading.value = true;
  try {
    const detail = await getIdentity(id);
    code.value = detail.code;
    name.value = detail.name;
    storyName.value = detail.storyName ?? '';
    publicIntro.value = detail.publicIntro ?? '';
    internalNote.value = detail.internalNote ?? '';
    members.value = (detail.members ?? []).map((m) => ({
      characterId: m.characterId,
      code: m.code,
      name: m.name,
      formLabel: m.formLabel ?? '',
      assetCount: m.assetCount ?? 0,
    }));
    selectedAssetIds.value = (detail.assets ?? []).map((a) => a.assetId);
    await syncAssetTableSelection();
  } catch (e) {
    ElMessage.error(apiError(e, '加载本体失败'));
    backToList();
  } finally {
    loading.value = false;
  }
}

function buildBasicPayload(): IdentityUpsertPayload | null {
  const trimmedName = name.value.trim();
  if (!trimmedName) {
    ElMessage.warning('请填写名称');
    return null;
  }
  return {
    name: trimmedName,
    storyName: storyName.value.trim() ? storyName.value.trim() : null,
    publicIntro: publicIntro.value.trim() ? publicIntro.value.trim() : null,
    internalNote: internalNote.value.trim() ? internalNote.value.trim() : null,
  };
}

async function save() {
  const payload = buildBasicPayload();
  if (!payload) return;
  saving.value = true;
  try {
    let id: number;
    if (isNew.value) {
      const created = await createIdentity(payload);
      id = created.id;
    } else {
      id = Number(identityId.value);
      await updateIdentity(id, payload);
    }

    await setIdentityMembers(
      id,
      members.value.map((m, i) => ({
        characterId: m.characterId,
        formLabel: m.formLabel.trim() ? m.formLabel.trim() : null,
        sortOrder: i + 1,
      })),
    );
    await setIdentityAssets(id, selectedAssetIds.value);

    ElMessage.success(isNew.value ? '已创建' : '已保存');
    if (isNew.value) {
      await router.replace({ name: 'character-identity-edit', params: { id: String(id) } });
    } else {
      await loadIdentity();
    }
  } catch (e) {
    ElMessage.error(apiError(e, '保存失败'));
  } finally {
    saving.value = false;
  }
}

watch(identityId, () => {
  void loadIdentity();
});

onMounted(async () => {
  await Promise.all([loadCharacters(), loadAvailableAssets(), loadIdentity()]);
});
</script>

<template>
  <section v-loading="loading" class="identity-editor-page">
    <div class="header">
      <div>
        <p class="eyebrow">CHARACTERS</p>
        <h2>{{ isNew ? '新建人物本体' : `编辑人物本体 #${identityId}` }}</h2>
      </div>
      <div class="actions">
        <el-button @click="backToList">返回列表</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </div>

    <el-form label-position="top" class="basic-form">
      <div class="form-grid">
        <el-form-item v-if="!isNew" label="编号">
          <el-input :model-value="code" disabled />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="name" maxlength="120" show-word-limit placeholder="本体名称" />
        </el-form-item>
        <el-form-item label="故事">
          <el-input v-model="storyName" maxlength="120" placeholder="可选" />
        </el-form-item>
      </div>
      <el-form-item label="公开简介">
        <el-input v-model="publicIntro" type="textarea" :rows="2" placeholder="可选" />
      </el-form-item>
      <el-form-item label="内部备注">
        <el-input v-model="internalNote" type="textarea" :rows="2" placeholder="可选" />
      </el-form-item>
    </el-form>

    <div class="panel">
      <div class="panel-head">
        <strong>映射形态</strong>
        <span class="muted">已选 {{ members.length }} 个</span>
      </div>
      <div class="members-layout">
        <div class="picker">
          <div class="picker-filters">
            <el-input
              v-model="characterQuery"
              clearable
              placeholder="搜索人物名称/编号"
              style="flex: 1"
            />
            <el-button :loading="charactersLoading" @click="loadCharacters">刷新</el-button>
          </div>
          <el-table
            v-loading="charactersLoading"
            :data="pickerCharacters"
            height="280"
            size="small"
            empty-text="无可用人物（已挂其他本体的不会显示）"
          >
            <el-table-column prop="name" label="人物" min-width="120" show-overflow-tooltip />
            <el-table-column prop="code" label="编号" width="100" />
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="addMember(row)">加入</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="selected">
          <div v-for="(m, index) in members" :key="m.characterId" class="member-row">
            <span class="badge">{{ index + 1 }}</span>
            <div class="member-meta">
              <div class="member-name" :title="m.name">{{ m.name }}</div>
              <div class="muted">{{ m.code }} · 素材 {{ m.assetCount }}</div>
            </div>
            <el-input
              v-model="m.formLabel"
              size="small"
              placeholder="形态标签"
              style="width: 120px"
            />
            <div class="member-actions">
              <el-button link :disabled="index === 0" @click="moveMember(index, -1)">上移</el-button>
              <el-button
                link
                :disabled="index === members.length - 1"
                @click="moveMember(index, 1)"
              >
                下移
              </el-button>
              <el-button link type="danger" @click="removeMember(index)">移除</el-button>
            </div>
          </div>
          <p v-if="!members.length" class="empty-hint">从左侧加入人物形态，可填标签并调整顺序</p>
        </div>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head">
        <strong>共用素材</strong>
        <span class="muted">已选 {{ selectedAssetIds.length }} 个 NORMAL 素材</span>
      </div>
      <div class="picker-filters">
        <el-input
          v-model="assetQuery"
          clearable
          placeholder="搜索素材名称"
          style="flex: 1"
          @keyup.enter="loadAvailableAssets"
        />
        <el-button :loading="assetsLoading" @click="loadAvailableAssets">筛选</el-button>
      </div>
      <el-table
        ref="assetTableRef"
        v-loading="assetsLoading"
        :data="availableAssets"
        height="320"
        size="small"
        empty-text="无可用 NORMAL 素材"
        row-key="id"
        @selection-change="onAssetSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="displayName" label="素材" min-width="180" show-overflow-tooltip />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="contentType" label="类型" width="120" show-overflow-tooltip />
      </el-table>
      <p class="muted note">保存时会全量替换本本体的共用素材关联。</p>
    </div>
  </section>
</template>

<style scoped>
.identity-editor-page {
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
  flex-wrap: wrap;
}
.basic-form {
  background: #f7f9fc;
  border: 1px solid #e6ebf2;
  border-radius: 8px;
  padding: 16px;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1.4fr 1.2fr;
  gap: 12px;
}
.panel {
  border: 1px solid #e6ebf2;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.members-layout {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 16px;
}
.picker-filters {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.member-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid #e6ebf2;
  border-radius: 6px;
  background: #fff;
  margin-bottom: 8px;
}
.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 11px;
  background: #1f4b7a;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}
.member-meta {
  flex: 1;
  min-width: 0;
}
.member-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.member-actions {
  display: flex;
  gap: 2px;
}
.muted {
  color: #7a8699;
  font-size: 13px;
}
.empty-hint,
.note {
  margin: 0;
  color: #7a8699;
  font-size: 13px;
}
@media (max-width: 960px) {
  .form-grid,
  .members-layout {
    grid-template-columns: 1fr;
  }
}
</style>
