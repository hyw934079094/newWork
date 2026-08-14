<script setup lang="ts">
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
          <img
            v-if="item.coverAssetId != null"
            :src="assetContentUrl(item.coverAssetId)"
            alt="画面组插画"
          />
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
</style>
