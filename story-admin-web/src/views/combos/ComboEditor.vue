<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import draggable from 'vuedraggable';
import {
  createCombo,
  getCombo,
  updateCombo,
  type ComboUpsertPayload,
} from '../../api/combo';
import { listAssets, type AssetItem } from '../../api/asset';
import { listCategories, type AssetCategoryItem } from '../../api/category';

interface MemberRow {
  assetId: number;
  displayName: string;
  contentType: string | null;
}

interface HoldRow {
  stepIndex: number | null;
  holdSeconds: number | null;
}

const route = useRoute();
const router = useRouter();

const comboId = computed(() => String(route.params.id ?? ''));
const isNew = computed(() => comboId.value === 'new');

const loading = ref(false);
const saving = ref(false);
const categories = ref<AssetCategoryItem[]>([]);
const categoryFilter = ref<number | null>(null);
const assetQuery = ref('');
const availableAssets = ref<AssetItem[]>([]);
const assetsLoading = ref(false);

const name = ref('');
const remark = ref('');
const defaultIntervalSec = ref(1);
const loopEnabled = ref(true);
const playSequence = ref('');
const members = ref<MemberRow[]>([]);
const stepHolds = ref<HoldRow[]>([]);

const memberAssetIds = computed(() => new Set(members.value.map((m) => m.assetId)));

const filteredAvailable = computed(() => {
  const q = assetQuery.value.trim().toLowerCase();
  return availableAssets.value.filter((a) => {
    if (memberAssetIds.value.has(a.id)) return false;
    if (!q) return true;
    return a.displayName.toLowerCase().includes(q);
  });
});

const sequenceHints = computed(() => {
  const hints: { type: 'success' | 'warning' | 'info'; text: string }[] = [];
  const raw = playSequence.value.trim();
  if (!raw) {
    hints.push({ type: 'warning', text: '播放序列必填，例如 1,2,1,3' });
    return hints;
  }
  if (members.value.length === 0) {
    hints.push({ type: 'warning', text: '请先添加成员后再填写序列' });
    return hints;
  }
  const parts = raw.split(',').map((p) => p.trim()).filter(Boolean);
  const maxNo = members.value.length;
  const invalid: string[] = [];
  const outOfRange: number[] = [];
  const steps: number[] = [];
  for (const token of parts) {
    if (!/^\d+$/.test(token)) {
      invalid.push(token);
      continue;
    }
    const n = Number(token);
    if (n < 1) {
      invalid.push(token);
      continue;
    }
    if (n > maxNo) {
      outOfRange.push(n);
      continue;
    }
    steps.push(n);
  }
  if (invalid.length) {
    hints.push({ type: 'warning', text: `非正整数项：${invalid.join(', ')}` });
  }
  if (outOfRange.length) {
    hints.push({
      type: 'warning',
      text: `超出成员编号 1..${maxNo}：${[...new Set(outOfRange)].join(', ')}`,
    });
  }
  if (!invalid.length && !outOfRange.length) {
    if (steps.length === 0) {
      hints.push({ type: 'warning', text: '播放序列解析后为空' });
    } else {
      hints.push({
        type: 'success',
        text: `共 ${steps.length} 步，成员编号均有效（1..${maxNo}）`,
      });
    }
  }
  return hints;
});

const sequenceStepCount = computed(() => {
  const raw = playSequence.value.trim();
  if (!raw) return 0;
  return raw
    .split(',')
    .map((p) => p.trim())
    .filter((t) => /^\d+$/.test(t) && Number(t) >= 1).length;
});

const holdHints = computed(() => {
  const hints: string[] = [];
  const len = sequenceStepCount.value;
  const seen = new Set<number>();
  for (const row of stepHolds.value) {
    if (row.stepIndex == null) {
      hints.push('存在未填写的步序号');
      continue;
    }
    if (len > 0 && (row.stepIndex < 1 || row.stepIndex > len)) {
      hints.push(`步序号 ${row.stepIndex} 超出序列长度 ${len}`);
    }
    if (seen.has(row.stepIndex)) {
      hints.push(`步序号 ${row.stepIndex} 重复`);
    }
    seen.add(row.stepIndex);
    if (row.holdSeconds == null || row.holdSeconds < 0.1) {
      hints.push(`步 ${row.stepIndex} 的停留秒数须 ≥ 0.1`);
    }
  }
  return [...new Set(hints)];
});

function apiError(e: unknown, fallback: string): string {
  const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
  return msg || fallback;
}

function backToList() {
  void router.push({ name: 'asset-combos' });
}

function suggestSequence() {
  if (members.value.length === 0) {
    playSequence.value = '';
    return;
  }
  playSequence.value = members.value.map((_, i) => String(i + 1)).join(',');
}

function addMember(asset: AssetItem) {
  if (memberAssetIds.value.has(asset.id)) return;
  members.value.push({
    assetId: asset.id,
    displayName: asset.displayName,
    contentType: asset.contentType,
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

function addHoldRow() {
  const nextIndex = stepHolds.value.length + 1;
  stepHolds.value.push({
    stepIndex: Math.min(nextIndex, Math.max(sequenceStepCount.value, 1)),
    holdSeconds: defaultIntervalSec.value,
  });
}

function removeHoldRow(index: number) {
  stepHolds.value.splice(index, 1);
}

async function loadCategories() {
  categories.value = await listCategories();
}

async function loadAvailableAssets() {
  assetsLoading.value = true;
  try {
    availableAssets.value = await listAssets({
      status: 'NORMAL',
      categoryId: categoryFilter.value ?? undefined,
      q: assetQuery.value.trim() || undefined,
    });
  } catch (e) {
    ElMessage.error(apiError(e, '加载素材失败'));
  } finally {
    assetsLoading.value = false;
  }
}

async function loadCombo() {
  if (isNew.value) {
    name.value = '';
    remark.value = '';
    defaultIntervalSec.value = 1;
    loopEnabled.value = true;
    playSequence.value = '';
    members.value = [];
    stepHolds.value = [];
    return;
  }
  const id = Number(comboId.value);
  if (!Number.isFinite(id)) {
    ElMessage.error('无效的组合 ID');
    backToList();
    return;
  }
  loading.value = true;
  try {
    const detail = await getCombo(id);
    name.value = detail.name;
    remark.value = detail.remark ?? '';
    defaultIntervalSec.value = Number(detail.defaultIntervalSec);
    loopEnabled.value = detail.loopEnabled;
    playSequence.value = detail.playSequence;
    members.value = (detail.members ?? [])
      .slice()
      .sort((a, b) => a.memberNo - b.memberNo)
      .map((m) => ({
        assetId: m.assetId,
        displayName: m.displayName || `素材 #${m.assetId}`,
        contentType: m.contentType,
      }));
    stepHolds.value = (detail.stepHolds ?? []).map((h) => ({
      stepIndex: h.stepIndex,
      holdSeconds: Number(h.holdSeconds),
    }));
  } catch (e) {
    ElMessage.error(apiError(e, '加载组合失败'));
    backToList();
  } finally {
    loading.value = false;
  }
}

function buildPayload(): ComboUpsertPayload | null {
  const trimmedName = name.value.trim();
  if (!trimmedName) {
    ElMessage.warning('请填写名称');
    return null;
  }
  if (defaultIntervalSec.value == null || defaultIntervalSec.value < 0.1) {
    ElMessage.warning('默认间隔须 ≥ 0.1 秒');
    return null;
  }
  if (members.value.length === 0) {
    ElMessage.warning('请至少添加一个成员');
    return null;
  }
  if (!playSequence.value.trim()) {
    ElMessage.warning('请填写播放序列');
    return null;
  }
  if (sequenceHints.value.some((h) => h.type === 'warning')) {
    ElMessage.warning(sequenceHints.value.find((h) => h.type === 'warning')!.text);
    return null;
  }
  if (holdHints.value.length) {
    ElMessage.warning(holdHints.value[0]);
    return null;
  }

  const holds = stepHolds.value
    .filter((h) => h.stepIndex != null && h.holdSeconds != null)
    .map((h) => ({
      stepIndex: h.stepIndex as number,
      holdSeconds: h.holdSeconds as number,
    }));

  return {
    name: trimmedName,
    playSequence: playSequence.value.trim(),
    defaultIntervalSec: defaultIntervalSec.value,
    loopEnabled: loopEnabled.value,
    remark: remark.value.trim() ? remark.value.trim() : null,
    members: members.value.map((m, i) => ({
      assetId: m.assetId,
      memberNo: i + 1,
    })),
    stepHolds: holds,
  };
}

async function save() {
  const payload = buildPayload();
  if (!payload) return;
  saving.value = true;
  try {
    if (isNew.value) {
      const created = await createCombo(payload);
      ElMessage.success('已创建');
      await router.replace({ name: 'asset-combo-edit', params: { id: String(created.id) } });
    } else {
      await updateCombo(Number(comboId.value), payload);
      ElMessage.success('已保存');
      await loadCombo();
    }
  } catch (e) {
    ElMessage.error(apiError(e, '保存失败'));
  } finally {
    saving.value = false;
  }
}

watch(categoryFilter, () => {
  void loadAvailableAssets();
});

watch(comboId, () => {
  void loadCombo();
});

onMounted(async () => {
  try {
    await loadCategories();
  } catch (e) {
    ElMessage.error(apiError(e, '加载分类失败'));
  }
  await Promise.all([loadAvailableAssets(), loadCombo()]);
});
</script>

<template>
  <section v-loading="loading" class="combo-editor-page">
    <div class="header">
      <div>
        <p class="eyebrow">ASSETS</p>
        <h2>{{ isNew ? '新建组合' : `编辑组合 #${comboId}` }}</h2>
      </div>
      <div class="actions">
        <el-button disabled title="预览见下阶段">预览（下阶段）</el-button>
        <el-button @click="backToList">返回列表</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </div>

    <el-form label-position="top" class="basic-form">
      <div class="form-grid">
        <el-form-item label="名称" required>
          <el-input v-model="name" maxlength="120" show-word-limit placeholder="组合名称" />
        </el-form-item>
        <el-form-item label="默认间隔（秒）" required>
          <el-input-number
            v-model="defaultIntervalSec"
            :min="0.1"
            :step="0.1"
            :precision="1"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="循环播放">
          <el-switch v-model="loopEnabled" />
        </el-form-item>
      </div>
      <el-form-item label="备注">
        <el-input v-model="remark" type="textarea" :rows="2" placeholder="可选" />
      </el-form-item>
    </el-form>

    <div class="panel">
      <div class="panel-head">
        <strong>成员（拖拽排序，编号 1..n）</strong>
        <span class="muted">已选 {{ members.length }} 个</span>
      </div>
      <div class="members-layout">
        <div class="picker">
          <div class="picker-filters">
            <el-select
              v-model="categoryFilter"
              clearable
              placeholder="全部分类"
              style="width: 160px"
            >
              <el-option
                v-for="c in categories"
                :key="c.id"
                :label="c.name"
                :value="c.id"
              />
            </el-select>
            <el-input
              v-model="assetQuery"
              clearable
              placeholder="搜索名称"
              style="flex: 1"
              @keyup.enter="loadAvailableAssets"
            />
            <el-button @click="loadAvailableAssets">筛选</el-button>
          </div>
          <el-table
            v-loading="assetsLoading"
            :data="filteredAvailable"
            height="280"
            size="small"
            empty-text="无可用 NORMAL 素材"
          >
            <el-table-column prop="displayName" label="素材" min-width="140" show-overflow-tooltip />
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="addMember(row)">加入</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="selected">
          <draggable
            v-model="members"
            item-key="assetId"
            :animation="150"
            handle=".drag-handle"
            ghost-class="member-ghost"
            class="member-list"
          >
            <template #item="{ element, index }">
              <div class="member-row">
                <span class="drag-handle" title="拖拽排序">☰</span>
                <span class="badge">{{ index + 1 }}</span>
                <span class="member-name" :title="element.displayName">{{ element.displayName }}</span>
                <span class="muted">#{{ element.assetId }}</span>
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
            </template>
          </draggable>
          <p v-if="!members.length" class="empty-hint">从左侧加入素材，拖拽或上下移调整顺序</p>
        </div>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head">
        <strong>播放序列</strong>
        <el-button link type="primary" @click="suggestSequence">填入 1..n</el-button>
      </div>
      <el-input
        v-model="playSequence"
        placeholder="逗号分隔成员编号，如 1,3,2,1"
      />
      <ul class="hints">
        <li v-for="(h, i) in sequenceHints" :key="i" :class="h.type">{{ h.text }}</li>
      </ul>
      <p class="muted note">排序决定成员编号；序列可跳号、可重复。个性化停留的步序号指序列中的第几步。</p>
    </div>

    <div class="panel">
      <div class="panel-head">
        <strong>个性化停留</strong>
        <el-button link type="primary" @click="addHoldRow">添加行</el-button>
      </div>
      <el-table :data="stepHolds" size="small" empty-text="无个性化停留（全部使用默认间隔）">
        <el-table-column label="步序号 (stepIndex)" min-width="160">
          <template #default="{ row }">
            <el-input-number
              v-model="row.stepIndex"
              :min="1"
              :step="1"
              controls-position="right"
            />
          </template>
        </el-table-column>
        <el-table-column label="停留秒数" min-width="160">
          <template #default="{ row }">
            <el-input-number
              v-model="row.holdSeconds"
              :min="0.1"
              :step="0.1"
              :precision="1"
              controls-position="right"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeHoldRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <ul v-if="holdHints.length" class="hints">
        <li v-for="(h, i) in holdHints" :key="i" class="warning">{{ h }}</li>
      </ul>
    </div>

    <p class="preview-note">预览播放器见下阶段（Task 6）</p>
  </section>
</template>

<style scoped>
.combo-editor-page {
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
  grid-template-columns: 2fr 1fr 1fr;
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
.member-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 80px;
}
.member-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid #e6ebf2;
  border-radius: 6px;
  background: #fff;
}
.member-ghost {
  opacity: 0.5;
}
.drag-handle {
  cursor: grab;
  color: #7a8699;
  user-select: none;
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
.member-name {
  flex: 1;
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
.note,
.preview-note {
  margin: 0;
  color: #7a8699;
  font-size: 13px;
}
.hints {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
}
.hints .warning {
  color: #b88230;
}
.hints .success {
  color: #2d8a4e;
}
.hints .info {
  color: #7a8699;
}
@media (max-width: 960px) {
  .form-grid,
  .members-layout {
    grid-template-columns: 1fr;
  }
}
</style>
