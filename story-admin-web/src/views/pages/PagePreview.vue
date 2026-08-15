<script setup lang="ts">
import { ref } from 'vue';
import { assetContentUrl } from '../../api/asset';

export type PagePreviewChild = {
  type: string;
  text?: string;
};

export type PagePreviewItem = {
  type: string;
  text?: string;
  coverAssetId?: number | null;
  children?: PagePreviewChild[];
};

defineProps<{
  items: PagePreviewItem[];
}>();

const lightboxAssetId = ref<number | null>(null);
const lightboxAlt = ref('');

function openLightbox(assetId: number, alt?: string) {
  lightboxAssetId.value = assetId;
  lightboxAlt.value = alt ?? '';
}

function closeLightbox() {
  lightboxAssetId.value = null;
  lightboxAlt.value = '';
}

function childClass(type: string): string {
  return type === 'DIALOGUE' ? 'dialogue' : 'body';
}
</script>

<template>
  <div class="page-preview">
    <p v-if="!items.length" class="preview-empty">暂无内容</p>
    <template v-for="(item, index) in items" :key="index">
      <h2 v-if="item.type === 'TITLE'" class="block-title">{{ item.text || '（无标题）' }}</h2>
      <p v-else-if="item.type === 'BODY'" class="block-body">{{ item.text }}</p>
      <hr v-else-if="item.type === 'DIVIDER'" class="block-divider" />
      <div v-else-if="item.type === 'BEAT'" class="beat">
        <div class="figure">
          <button
            v-if="item.coverAssetId != null"
            type="button"
            class="figure-btn"
            title="查看大图"
            @click="openLightbox(item.coverAssetId, '画面组插画')"
          >
            <img :src="assetContentUrl(item.coverAssetId)" alt="画面组插画" />
          </button>
          <div v-else class="figure-placeholder">未选择封面</div>
        </div>
        <div v-if="item.children?.length" class="children">
          <p
            v-for="(child, ci) in item.children"
            :key="ci"
            :class="childClass(child.type)"
          >
            {{ child.text }}
          </p>
        </div>
      </div>
      <p v-else class="block-body">{{ item.text }}</p>
    </template>

    <Teleport to="body">
      <div
        v-if="lightboxAssetId != null"
        class="preview-lightbox"
        role="dialog"
        aria-modal="true"
        @click="closeLightbox"
      >
        <img
          class="preview-lightbox-img"
          :src="assetContentUrl(lightboxAssetId)"
          :alt="lightboxAlt"
          @click.stop
        />
      </div>
    </Teleport>
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
.beat .figure + .children {
  margin-top: var(--gap-figure-text);
}
.beat .children > * + * {
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
.preview-lightbox {
  position: fixed;
  inset: 0;
  z-index: 4000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: rgba(12, 18, 32, 0.72);
  cursor: zoom-out;
}
.preview-lightbox-img {
  max-width: min(860px, 92vw);
  max-height: 88vh;
  object-fit: contain;
  border-radius: 10px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  background: #111827;
  cursor: default;
}
</style>
