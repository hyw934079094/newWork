<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { assetContentUrl } from '../../api/asset';
import { getCombo, type ComboDetail } from '../../api/combo';
import ImageLightbox from '../../components/ImageLightbox.vue';
import ComboPreviewPlayer from '../combos/ComboPreviewPlayer.vue';

export type PagePreviewChild = {
  type: string;
  text?: string;
  assetId?: number | null;
  comboId?: number | null;
};

export type PagePreviewItem = {
  type: string;
  text?: string;
  coverAssetId?: number | null;
  children?: PagePreviewChild[];
};

const props = defineProps<{
  items: PagePreviewItem[];
}>();

const lightboxVisible = ref(false);
const lightboxAssetId = ref<number | null>(null);
const lightboxAlt = ref('');
const comboCache = reactive<Record<number, ComboDetail | null>>({});
const comboLoading = reactive<Record<number, boolean>>({});

const lightboxSrc = computed(() =>
  lightboxAssetId.value != null ? assetContentUrl(lightboxAssetId.value) : null,
);

function openLightbox(assetId: number, alt?: string) {
  lightboxAssetId.value = assetId;
  lightboxAlt.value = alt ?? '';
  lightboxVisible.value = true;
}

function childClass(type: string): string {
  return type === 'DIALOGUE' ? 'dialogue' : 'body';
}

function beatNodes(item: PagePreviewItem): PagePreviewChild[] {
  const children = item.children ?? [];
  const hasVisual = children.some((c) => c.type === 'COVER' || c.type === 'COMBO');
  if (hasVisual) {
    return children;
  }
  const nodes: PagePreviewChild[] = [];
  if (item.coverAssetId != null) {
    nodes.push({ type: 'COVER', assetId: item.coverAssetId });
  }
  for (const child of children) {
    if (child.type !== 'COVER' && child.type !== 'COMBO') {
      nodes.push(child);
    }
  }
  return nodes;
}

function coverAssetIdOf(child: PagePreviewChild, item: PagePreviewItem): number | null {
  if (typeof child.assetId === 'number' && Number.isFinite(child.assetId)) {
    return child.assetId;
  }
  return item.coverAssetId ?? null;
}

async function ensureCombo(comboId: number) {
  if (comboCache[comboId] !== undefined || comboLoading[comboId]) {
    return;
  }
  comboLoading[comboId] = true;
  try {
    comboCache[comboId] = await getCombo(comboId);
  } catch {
    comboCache[comboId] = null;
  } finally {
    comboLoading[comboId] = false;
  }
}

function collectComboIds(items: PagePreviewItem[]): number[] {
  const ids = new Set<number>();
  for (const item of items) {
    if (item.type !== 'BEAT') continue;
    for (const child of beatNodes(item)) {
      if (child.type === 'COMBO' && typeof child.comboId === 'number') {
        ids.add(child.comboId);
      }
    }
  }
  return [...ids];
}

watch(
  () => props.items,
  (items) => {
    for (const id of collectComboIds(items)) {
      void ensureCombo(id);
    }
  },
  { immediate: true, deep: true },
);

onMounted(() => {
  for (const id of collectComboIds(props.items)) {
    void ensureCombo(id);
  }
});
</script>

<template>
  <div class="page-preview">
    <p v-if="!items.length" class="preview-empty">暂无内容</p>
    <template v-for="(item, index) in items" :key="index">
      <h2 v-if="item.type === 'TITLE'" class="block-title">{{ item.text || '（无标题）' }}</h2>
      <p v-else-if="item.type === 'BODY'" class="block-body">{{ item.text }}</p>
      <hr v-else-if="item.type === 'DIVIDER'" class="block-divider" />
      <div v-else-if="item.type === 'BEAT'" class="beat">
        <template v-for="(child, ci) in beatNodes(item)" :key="ci">
          <div v-if="child.type === 'COVER'" class="figure">
            <button
              v-if="coverAssetIdOf(child, item) != null"
              type="button"
              class="figure-btn"
              title="查看大图"
              @click="openLightbox(coverAssetIdOf(child, item)!, '画面组插画')"
            >
              <img :src="assetContentUrl(coverAssetIdOf(child, item)!)" alt="画面组插画" />
            </button>
            <div v-else class="figure-placeholder">未选择封面</div>
          </div>
          <div v-else-if="child.type === 'COMBO'" class="figure combo-figure">
            <p v-if="child.comboId == null" class="figure-placeholder">未选择组合</p>
            <p v-else-if="comboLoading[child.comboId]" class="figure-placeholder">组合加载中…</p>
            <p v-else-if="!comboCache[child.comboId]" class="figure-placeholder">组合不可用</p>
            <ComboPreviewPlayer
              v-else
              :members="comboCache[child.comboId]!.members"
              :play-sequence="comboCache[child.comboId]!.playSequence"
              :default-interval-sec="Number(comboCache[child.comboId]!.defaultIntervalSec)"
              :loop-enabled="comboCache[child.comboId]!.loopEnabled"
              :step-holds="comboCache[child.comboId]!.stepHolds"
              autoplay
              compact
            />
          </div>
          <p v-else :class="childClass(child.type)">
            {{ child.text }}
          </p>
        </template>
      </div>
      <p v-else class="block-body">{{ item.text }}</p>
    </template>

    <ImageLightbox v-model="lightboxVisible" :src="lightboxSrc" :alt="lightboxAlt" />
  </div>
</template>

<style scoped>
.page-preview {
  --gap-beat: 44px;
  --gap-figure-text: 15px;
  --gap-inline: 9px;
  max-width: 720px;
  margin: 0 auto;
  color: #1f2a44;
  line-height: 1.75;
}
.page-preview > * + * {
  margin-top: var(--gap-beat);
}
.beat > * + * {
  margin-top: var(--gap-figure-text);
}
.beat .figure + p,
.beat p + .figure,
.beat p + p {
  margin-top: var(--gap-inline);
}
.preview-empty {
  margin: 0;
  color: #9aa6bf;
  text-align: center;
  padding: 48px 12px;
}
.block-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.4;
}
.block-body,
.body {
  margin: 0;
  font-size: 15px;
  white-space: pre-wrap;
  word-break: break-word;
}
.block-divider {
  border: none;
  border-top: 1px solid #d8deea;
  margin: 0;
}
.figure-btn {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: zoom-in;
  border-radius: 10px;
}
.figure-btn:focus-visible {
  outline: 2px solid #2f6fed;
  outline-offset: 2px;
}
.beat .figure img {
  width: 100%;
  display: block;
  border-radius: 10px;
  background: #eef1f7;
}
.figure-placeholder {
  min-height: 160px;
  border-radius: 10px;
  background: #f4f6fa;
  color: #9aa6bf;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  margin: 0;
}
.combo-figure {
  border-radius: 10px;
  overflow: hidden;
}
.dialogue {
  margin: 0;
  font-size: 15px;
  white-space: pre-wrap;
  word-break: break-word;
  padding-left: 12px;
  border-left: 3px solid #c5d0e6;
  color: #33415f;
}
</style>
