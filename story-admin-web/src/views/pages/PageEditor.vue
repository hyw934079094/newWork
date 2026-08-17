<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import draggable from 'vuedraggable';
import { assetContentUrl, listAssets, type AssetItem } from '../../api/asset';
import { listCategories, type AssetCategoryItem } from '../../api/category';
import { getPage, updatePage } from '../../api/page';
import PagePreview, { type PagePreviewItem } from './PagePreview.vue';

type TopType = 'TITLE' | 'BODY' | 'DIVIDER' | 'BEAT';
type ChildType = 'COVER' | 'BODY' | 'DIALOGUE';
type TextChildType = 'BODY' | 'DIALOGUE';

interface ChildItem {
  uid: string;
  type: ChildType;
  text: string;
  assetId: number | null;
}

interface TimelineItem {
  uid: string;
  type: TopType;
  text: string;
  children: ChildItem[];
}

const TOP_LABELS: Record<TopType, string> = {
  TITLE: '标题',
  BODY: '正文',
  DIVIDER: '分隔',
  BEAT: '画面组',
};

const TEXT_CHILD_LABELS: Record<TextChildType, string> = {
  BODY: '正文',
  DIALOGUE: '对话',
};

const route = useRoute();
const router = useRouter();
const pageId = computed(() => Number(route.params.pageId));

const loading = ref(false);
const saving = ref(false);
const title = ref('');
const arcId = ref<number | null>(null);
const items = ref<TimelineItem[]>([]);
let uidSeq = 0;

const pickerVisible = ref(false);
const pickerCategoryId = ref<number | 'all'>('all');
const pickerKeyword = ref('');
const pickerAssets = ref<AssetItem[]>([]);
const pickerSelectedId = ref<number | null>(null);
const pickerLoading = ref(false);
const pickerBeatUid = ref<string | null>(null);
const categories = ref<AssetCategoryItem[]>([]);

const previewItems = computed<PagePreviewItem[]>(() =>
  items.value.map((item) => {
    if (item.type !== 'BEAT') {
      return { type: item.type, text: item.text };
    }
    const cover = findCover(item);
    return {
      type: 'BEAT',
      coverAssetId: cover?.assetId ?? null,
      children: item.children.map((child) =>
        child.type === 'COVER'
          ? { type: 'COVER', assetId: child.assetId }
          : { type: child.type, text: child.text },
      ),
    };
  }),
);

function nextUid(): string {
  uidSeq += 1;
  return `item-${uidSeq}`;
}

function apiError(e: unknown, fallback: string): string {
  const err = e as {
    response?: { data?: { message?: string; error?: string } };
    message?: string;
  };
  const msg = err.response?.data?.message || err.response?.data?.error;
  return msg?.trim() || fallback;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return null;
}

function findCover(item: TimelineItem): ChildItem | undefined {
  return item.children.find((c) => c.type === 'COVER');
}

function ensureCoverChild(children: ChildItem[], coverAssetId: number | null): ChildItem[] {
  const coverCount = children.filter((c) => c.type === 'COVER').length;
  if (coverCount === 0) {
    return [
      { uid: nextUid(), type: 'COVER', text: '', assetId: coverAssetId },
      ...children,
    ];
  }
  const result: ChildItem[] = [];
  let coverPlaced = false;
  for (const child of children) {
    if (child.type !== 'COVER') {
      result.push(child);
      continue;
    }
    if (coverPlaced) {
      continue;
    }
    result.push({
      ...child,
      assetId: child.assetId ?? coverAssetId,
    });
    coverPlaced = true;
  }
  return result;
}

function parseChild(node: unknown): ChildItem | null {
  const obj = asRecord(node);
  if (!obj) return null;
  if (obj.type === 'COVER') {
    const asset = obj.assetId;
    return {
      uid: nextUid(),
      type: 'COVER',
      text: '',
      assetId: typeof asset === 'number' && Number.isFinite(asset) ? asset : null,
    };
  }
  if (obj.type === 'DIALOGUE' || obj.type === 'BODY') {
    return {
      uid: nextUid(),
      type: obj.type,
      text: typeof obj.text === 'string' ? obj.text : '',
      assetId: null,
    };
  }
  return null;
}

function parseTop(node: unknown): TimelineItem | null {
  const obj = asRecord(node);
  if (!obj) {
    return null;
  }
  const type = obj.type;
  if (type === 'BEAT') {
    const cover = obj.coverAssetId;
    const coverAssetId = typeof cover === 'number' && Number.isFinite(cover) ? cover : null;
    const childrenRaw = Array.isArray(obj.children) ? obj.children : [];
    const parsed = childrenRaw
      .map(parseChild)
      .filter((child): child is ChildItem => child != null);
    return {
      uid: nextUid(),
      type: 'BEAT',
      text: '',
      children: ensureCoverChild(parsed, coverAssetId),
    };
  }
  if (type === 'DIVIDER') {
    return { uid: nextUid(), type: 'DIVIDER', text: '', children: [] };
  }
  if (type === 'TITLE' || type === 'BODY') {
    return {
      uid: nextUid(),
      type,
      text: typeof obj.text === 'string' ? obj.text : '',
      children: [],
    };
  }
  return null;
}

function parseContent(raw?: string | null): TimelineItem[] {
  let parsed: unknown = [];
  try {
    const source = typeof raw === 'string' && raw.trim() ? raw : '[]';
    parsed = JSON.parse(source);
  } catch {
    parsed = [];
  }
  if (!Array.isArray(parsed)) {
    return [];
  }
  return parsed.map(parseTop).filter((item): item is TimelineItem => item != null);
}

function serializeItems(list: TimelineItem[]): unknown[] {
  return list.map((item) => {
    if (item.type === 'BEAT') {
      const cover = findCover(item);
      return {
        type: 'BEAT',
        coverAssetId: cover?.assetId ?? null,
        children: item.children.map((child) =>
          child.type === 'COVER'
            ? { type: 'COVER', assetId: child.assetId }
            : { type: child.type, text: child.text },
        ),
      };
    }
    if (item.type === 'DIVIDER') {
      return { type: 'DIVIDER' };
    }
    return { type: item.type, text: item.text };
  });
}

function createTop(type: TopType): TimelineItem {
  if (type === 'BEAT') {
    return {
      uid: nextUid(),
      type: 'BEAT',
      text: '',
      children: [{ uid: nextUid(), type: 'COVER', text: '', assetId: null }],
    };
  }
  return {
    uid: nextUid(),
    type,
    text: '',
    children: [],
  };
}

function addTop(type: TopType) {
  items.value = [...items.value, createTop(type)];
}

function removeTop(index: number) {
  items.value = items.value.filter((_, i) => i !== index);
}

function moveTop(index: number, dir: -1 | 1) {
  const next = index + dir;
  if (next < 0 || next >= items.value.length) return;
  const copy = [...items.value];
  const current = copy[index];
  const neighbor = copy[next];
  if (current == null || neighbor == null) return;
  copy[index] = neighbor;
  copy[next] = current;
  items.value = copy;
}

function addChild(item: TimelineItem, type: TextChildType) {
  item.children = [...item.children, { uid: nextUid(), type, text: '', assetId: null }];
}

function removeChild(item: TimelineItem, index: number) {
  const child = item.children[index];
  if (child?.type === 'COVER') {
    ElMessage.warning('封面节点不可删除，可清除或更换素材');
    return;
  }
  item.children = item.children.filter((_, i) => i !== index);
}

function moveChild(item: TimelineItem, index: number, dir: -1 | 1) {
  const next = index + dir;
  if (next < 0 || next >= item.children.length) return;
  const copy = [...item.children];
  const current = copy[index];
  const neighbor = copy[next];
  if (current == null || neighbor == null) return;
  copy[index] = neighbor;
  copy[next] = current;
  item.children = copy;
}

function clearCover(item: TimelineItem) {
  const cover = findCover(item);
  if (cover) {
    cover.assetId = null;
  }
}

async function openCoverPicker(item: TimelineItem) {
  pickerBeatUid.value = item.uid;
  pickerSelectedId.value = findCover(item)?.assetId ?? null;
  pickerCategoryId.value = 'all';
  pickerKeyword.value = '';
  pickerVisible.value = true;
  if (!categories.value.length) {
    categories.value = await listCategories();
  }
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

function selectPickerAsset(id: number) {
  pickerSelectedId.value = pickerSelectedId.value === id ? null : id;
}

function isPickerSelected(id: number): boolean {
  return pickerSelectedId.value === id;
}

function confirmCoverPicker() {
  const target = items.value.find((item) => item.uid === pickerBeatUid.value);
  if (target) {
    let cover = findCover(target);
    if (!cover) {
      cover = { uid: nextUid(), type: 'COVER', text: '', assetId: null };
      target.children = [cover, ...target.children];
    }
    cover.assetId = pickerSelectedId.value;
  }
  pickerVisible.value = false;
}

async function load() {
  const id = pageId.value;
  if (!Number.isFinite(id) || id <= 0) {
    title.value = '';
    items.value = [];
    arcId.value = null;
    ElMessage.error('页面无效');
    return;
  }
  loading.value = true;
  try {
    const page = await getPage(id);
    title.value = page.title ?? '';
    arcId.value = page.arcId ?? null;
    items.value = parseContent(page.contentJson);
  } catch (e: unknown) {
    ElMessage.error(apiError(e, '加载页面失败'));
  } finally {
    loading.value = false;
  }
}

function goList() {
  if (arcId.value != null) {
    void router.push(`/arcs/${arcId.value}/pages`);
    return;
  }
  router.back();
}

async function save() {
  const id = pageId.value;
  if (!Number.isFinite(id) || id <= 0) {
    ElMessage.error('页面无效');
    return;
  }
  if (!title.value.trim()) {
    ElMessage.warning('请填写页面标题');
    return;
  }
  const missingCover = items.value.find((item) => {
    if (item.type !== 'BEAT') return false;
    const cover = findCover(item);
    return cover == null || cover.assetId == null || !Number.isInteger(cover.assetId);
  });
  if (missingCover) {
    ElMessage.warning('画面组必须选择封面素材');
    return;
  }
  saving.value = true;
  try {
    const payloadItems = serializeItems(items.value);
    await updatePage(id, {
      title: title.value.trim(),
      contentJson: JSON.stringify(payloadItems),
    });
    ElMessage.success('已保存');
    await load();
  } catch (e: unknown) {
    ElMessage.error(apiError(e, '保存失败'));
  } finally {
    saving.value = false;
  }
}

watch(pageId, () => {
  void load();
});

onMounted(() => {
  void load();
});
</script>

<template>
  <section v-loading="loading" class="page-editor-page">
    <div class="header">
      <div>
        <p class="eyebrow">EDITOR</p>
        <h2>页面编辑器</h2>
      </div>
      <div class="header-actions">
        <el-button @click="goList">返回页面列表</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </div>

    <el-form class="title-form" label-width="72px" @submit.prevent="save">
      <el-form-item label="标题" required>
        <el-input v-model="title" placeholder="页面标题" />
      </el-form-item>
    </el-form>

    <div class="editor-layout">
      <div class="panel timeline-panel">
        <div class="panel-head">
          <strong>时间线</strong>
          <span class="muted">拖动手柄排序 · 顶层 {{ items.length }}</span>
        </div>
        <div class="add-row">
          <el-button size="small" @click="addTop('TITLE')">添加标题</el-button>
          <el-button size="small" @click="addTop('BODY')">添加正文</el-button>
          <el-button size="small" @click="addTop('DIVIDER')">添加分隔</el-button>
          <el-button size="small" type="primary" @click="addTop('BEAT')">添加画面组</el-button>
        </div>

        <p v-if="!items.length" class="empty-hint">点击上方按钮添加内容块</p>

        <draggable
          v-model="items"
          item-key="uid"
          handle=".drag-handle"
          class="timeline-list"
        >
          <template #item="{ element: item, index }">
            <div class="timeline-card" :class="`is-${item.type.toLowerCase()}`">
              <div class="card-head">
                <div class="card-head-left">
                  <span class="drag-handle" title="拖拽排序">⋮⋮</span>
                  <span class="type-badge">{{ TOP_LABELS[item.type as TopType] }}</span>
                </div>
                <div class="card-actions">
                  <el-button link type="primary" :disabled="index === 0" @click="moveTop(index, -1)">
                    上移
                  </el-button>
                  <el-button
                    link
                    type="primary"
                    :disabled="index === items.length - 1"
                    @click="moveTop(index, 1)"
                  >
                    下移
                  </el-button>
                  <el-button link type="danger" @click="removeTop(index)">删除</el-button>
                </div>
              </div>

              <el-input
                v-if="item.type === 'TITLE'"
                v-model="item.text"
                placeholder="标题文字"
              />
              <el-input
                v-else-if="item.type === 'BODY'"
                v-model="item.text"
                type="textarea"
                :rows="3"
                placeholder="正文"
              />
              <p v-else-if="item.type === 'DIVIDER'" class="divider-hint">阅读时显示为分隔线</p>

              <div v-else-if="item.type === 'BEAT'" class="beat-editor">
                <div class="child-head">
                  <span>组内块（封面可拖到文上/文下）</span>
                  <div class="child-add">
                    <el-button size="small" @click="addChild(item, 'BODY')">添加正文</el-button>
                    <el-button size="small" @click="addChild(item, 'DIALOGUE')">添加对话</el-button>
                  </div>
                </div>

                <draggable
                  v-model="item.children"
                  item-key="uid"
                  handle=".drag-handle"
                  class="child-list"
                >
                  <template #item="{ element: child, index: ci }">
                    <div class="child-card" :class="{ 'is-cover': child.type === 'COVER' }">
                      <div class="card-head">
                        <div class="card-head-left">
                          <span class="drag-handle" title="拖拽排序">⋮⋮</span>
                          <span v-if="child.type === 'COVER'" class="type-badge cover-badge">封面</span>
                          <el-select
                            v-else
                            v-model="child.type"
                            size="small"
                            class="child-type"
                          >
                            <el-option
                              v-for="(label, value) in TEXT_CHILD_LABELS"
                              :key="value"
                              :label="label"
                              :value="value"
                            />
                          </el-select>
                        </div>
                        <div class="card-actions">
                          <el-button
                            link
                            type="primary"
                            :disabled="ci === 0"
                            @click="moveChild(item, ci, -1)"
                          >
                            上移
                          </el-button>
                          <el-button
                            link
                            type="primary"
                            :disabled="ci === item.children.length - 1"
                            @click="moveChild(item, ci, 1)"
                          >
                            下移
                          </el-button>
                          <el-button
                            v-if="child.type !== 'COVER'"
                            link
                            type="danger"
                            @click="removeChild(item, ci)"
                          >
                            删除
                          </el-button>
                        </div>
                      </div>

                      <div v-if="child.type === 'COVER'" class="cover-editor">
                        <div class="cover-preview">
                          <img
                            v-if="child.assetId != null"
                            :src="assetContentUrl(child.assetId)"
                            alt="画面组封面"
                          />
                          <span v-else class="cover-placeholder">暂无封面</span>
                        </div>
                        <div class="cover-actions">
                          <el-button size="small" @click="openCoverPicker(item)">选择封面</el-button>
                          <el-button
                            size="small"
                            :disabled="child.assetId == null"
                            @click="clearCover(item)"
                          >
                            清除
                          </el-button>
                        </div>
                      </div>
                      <el-input
                        v-else
                        v-model="child.text"
                        type="textarea"
                        :rows="2"
                        :placeholder="child.type === 'DIALOGUE' ? '对话内容' : '正文'"
                      />
                    </div>
                  </template>
                </draggable>
              </div>
            </div>
          </template>
        </draggable>
      </div>

      <div class="panel preview-panel">
        <div class="panel-head">
          <strong>预览</strong>
          <span class="muted">按时间线顺序 · 只读</span>
        </div>
        <PagePreview :items="previewItems" />
      </div>
    </div>

    <el-dialog
      v-model="pickerVisible"
      title="选择封面"
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
          @click="selectPickerAsset(asset.id)"
        >
          <img :src="assetContentUrl(asset.id)" :alt="asset.displayName" />
          <span class="picker-card-title" :title="asset.displayName">{{ asset.displayName }}</span>
        </button>
        <p v-if="!pickerLoading && !pickerAssets.length" class="hint picker-empty">暂无 NORMAL 素材</p>
      </div>

      <template #footer>
        <div class="picker-footer">
          <span class="picker-count">
            {{ pickerSelectedId != null ? '已选 1 项' : '未选择' }}
          </span>
          <div class="picker-footer-actions">
            <el-button @click="pickerVisible = false">取消</el-button>
            <el-button type="primary" @click="confirmCoverPicker">确定</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page-editor-page {
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
.header-actions {
  display: flex;
  gap: 8px;
}
.eyebrow {
  margin: 0;
  color: #6f7e9d;
  font-size: 12px;
  letter-spacing: 0.08em;
}
.header h2 {
  margin: 8px 0 0;
}
.title-form {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 10px 30px #24325212;
  padding: 12px 16px 4px;
}
.editor-layout {
  display: grid;
  grid-template-columns: minmax(340px, 1fr) minmax(340px, 1fr);
  gap: 16px;
  align-items: start;
}
.panel {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 10px 30px #24325212;
  padding: 16px;
  min-height: 420px;
}
.preview-panel {
  position: sticky;
  top: 12px;
  max-height: calc(100vh - 96px);
  overflow: auto;
}
.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}
.muted {
  color: #6f7e9d;
  font-size: 13px;
}
.add-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.empty-hint {
  margin: 8px 0 0;
  color: #9aa6bf;
  font-size: 13px;
}
.timeline-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.timeline-card {
  border: 1px solid #e6ebf2;
  border-radius: 12px;
  padding: 12px;
  margin-top: 12px;
  background: #fbfcfe;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}
.card-head-left {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.drag-handle {
  cursor: grab;
  color: #94a3b8;
  user-select: none;
  font-size: 14px;
  line-height: 1;
  padding: 2px 4px;
}
.drag-handle:active {
  cursor: grabbing;
}
.type-badge {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: #eef3ff;
  color: #3a6ff0;
  font-size: 12px;
  font-weight: 600;
}
.cover-badge {
  background: #eef8f1;
  color: #2f7a4a;
}
.card-actions {
  display: inline-flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 2px;
  white-space: nowrap;
}
.divider-hint {
  margin: 0;
  color: #9aa6bf;
  font-size: 13px;
}
.beat-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.cover-editor {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.cover-preview {
  width: 96px;
  height: 96px;
  border-radius: 12px;
  background: #f4f6fa;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.cover-placeholder {
  color: #9aa6bf;
  font-size: 12px;
}
.cover-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.child-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  color: #33415f;
}
.child-add {
  display: flex;
  gap: 8px;
}
.child-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.child-card {
  border: 1px dashed #d8deea;
  border-radius: 10px;
  padding: 10px;
  background: #fff;
}
.child-card.is-cover {
  border-style: solid;
  border-color: #c5dccb;
  background: #f7fbf8;
}
.child-type {
  width: 108px;
}
.hint {
  margin: 0;
  color: #6f7e9d;
  font-size: 13px;
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
@media (max-width: 960px) {
  .editor-layout {
    grid-template-columns: 1fr;
  }
  .preview-panel {
    position: static;
    max-height: none;
  }
}
</style>
