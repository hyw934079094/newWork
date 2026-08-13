<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import draggable from 'vuedraggable';
import {
  getCurrentAiReference,
  replaceCurrentAiReferenceItems,
  type AiReferenceItemPayload,
} from '../../api/aiReference';
import { assetContentUrl, listAssets, type AssetItem } from '../../api/asset';

interface RefRow {
  key: string;
  assetId: number;
  displayName: string;
  purpose: string;
  note: string;
  strength: number | null;
}

const loading = ref(false);
const saving = ref(false);
const sessionName = ref('default');
const library = ref<AssetItem[]>([]);
const selectedIds = ref<number[]>([]);
const rows = ref<RefRow[]>([]);

const libraryMap = computed(() => {
  const map = new Map<number, AssetItem>();
  for (const a of library.value) map.set(a.id, a);
  return map;
});

function apiError(e: unknown, fallback: string): string {
  const err = e as { response?: { data?: { message?: string } } };
  return err?.response?.data?.message || fallback;
}

function makeKey(assetId: number): string {
  return `${assetId}-${Math.random().toString(36).slice(2, 8)}`;
}

async function load() {
  loading.value = true;
  try {
    const [assets, session] = await Promise.all([
      listAssets({ status: 'NORMAL' }),
      getCurrentAiReference(),
    ]);
    library.value = assets;
    sessionName.value = session.name || 'default';
    const byId = new Map(assets.map((a) => [a.id, a]));
    const ordered = [...(session.items || [])].sort(
      (a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0),
    );
    rows.value = ordered.map((item) => ({
      key: makeKey(item.assetId),
      assetId: item.assetId,
      displayName: byId.get(item.assetId)?.displayName || `#${item.assetId}`,
      purpose: item.purpose || '',
      note: item.note || '',
      strength: item.strength ?? null,
    }));
    selectedIds.value = ordered.map((i) => i.assetId);
  } catch (e) {
    ElMessage.error(apiError(e, '加载 AI 参考区失败'));
  } finally {
    loading.value = false;
  }
}

function onSelectionChange(ids: number[]) {
  const kept = rows.value.filter((r) => ids.includes(r.assetId));
  const keptIds = new Set(kept.map((r) => r.assetId));
  const appended = ids
    .filter((id) => !keptIds.has(id))
    .map((id) => {
      const asset = libraryMap.value.get(id);
      return {
        key: makeKey(id),
        assetId: id,
        displayName: asset?.displayName || `#${id}`,
        purpose: '',
        note: '',
        strength: null as number | null,
      };
    });
  rows.value = [...kept, ...appended];
  selectedIds.value = ids;
}

async function save() {
  saving.value = true;
  try {
    const payload: AiReferenceItemPayload[] = rows.value.map((r) => ({
      assetId: r.assetId,
      purpose: r.purpose.trim() || null,
      note: r.note.trim() || null,
      strength: r.strength,
    }));
    const session = await replaceCurrentAiReferenceItems(payload);
    sessionName.value = session.name || 'default';
    ElMessage.success('已保存参考区（未调用模型）');
    await load();
  } catch (e) {
    ElMessage.error(apiError(e, '保存失败'));
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section v-loading="loading" class="ai-page">
    <div class="header">
      <div>
        <p class="eyebrow">AI REFERENCE</p>
        <h2>AI 参考区</h2>
        <p class="hint">单例会话「{{ sessionName }}」· 仅保存素材顺序与用途，不调用模型</p>
      </div>
      <div class="actions">
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存参考区</el-button>
      </div>
    </div>

    <div class="layout">
      <aside class="library">
        <h3>素材库勾选</h3>
        <el-checkbox-group :model-value="selectedIds" @change="onSelectionChange">
          <label v-for="asset in library" :key="asset.id" class="lib-row">
            <el-checkbox :value="asset.id" :label="asset.id">
              <span class="lib-label">
                <img :src="assetContentUrl(asset.id)" :alt="asset.displayName" />
                <span>{{ asset.displayName }}</span>
              </span>
            </el-checkbox>
          </label>
        </el-checkbox-group>
        <p v-if="!library.length" class="empty">暂无素材，请先在素材管理上传</p>
      </aside>

      <div class="ordered">
        <h3>参考顺序与用途</h3>
        <draggable
          v-model="rows"
          item-key="key"
          handle=".drag-handle"
          class="ref-list"
        >
          <template #item="{ element, index }">
            <div class="ref-card">
              <span class="drag-handle" title="拖拽排序">⋮⋮</span>
              <span class="idx">{{ index + 1 }}</span>
              <img :src="assetContentUrl(element.assetId)" :alt="element.displayName" />
              <div class="fields">
                <strong>{{ element.displayName }}</strong>
                <el-input v-model="element.purpose" placeholder="用途，如：外貌 / 服装" />
                <el-input v-model="element.note" type="textarea" :rows="2" placeholder="备注" />
                <el-input-number
                  v-model="element.strength"
                  :min="0"
                  :max="100"
                  :step="0.1"
                  :precision="2"
                  controls-position="right"
                  placeholder="强度"
                />
              </div>
            </div>
          </template>
        </draggable>
        <p v-if="!rows.length" class="empty">从左侧勾选素材加入参考区</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.ai-page {
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
  font-size: 12px;
  letter-spacing: 0.08em;
  color: #64748b;
}
h2 {
  margin: 4px 0;
}
.hint {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}
.actions {
  display: flex;
  gap: 8px;
}
.layout {
  display: grid;
  grid-template-columns: minmax(240px, 320px) 1fr;
  gap: 20px;
  align-items: start;
}
.library,
.ordered {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 14px;
  min-height: 320px;
}
h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
.lib-row {
  display: block;
  margin-bottom: 8px;
}
.lib-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.lib-label img {
  width: 36px;
  height: 36px;
  object-fit: cover;
  border-radius: 4px;
  background: #fff;
}
.ref-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ref-card {
  display: grid;
  grid-template-columns: 24px 28px 72px 1fr;
  gap: 10px;
  align-items: start;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px;
}
.drag-handle {
  cursor: grab;
  color: #94a3b8;
  user-select: none;
  padding-top: 4px;
}
.idx {
  font-weight: 600;
  color: #475569;
  padding-top: 4px;
}
.ref-card img {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: 6px;
  background: #f1f5f9;
}
.fields {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.empty {
  color: #94a3b8;
  font-size: 13px;
}
@media (max-width: 900px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
