<script setup lang="ts">
import { computed, ref } from 'vue';
import { assetContentUrl } from '../api/asset';
import ImageLightbox from './ImageLightbox.vue';

const props = withDefaults(
  defineProps<{
    assetId: number;
    alt?: string;
    size?: number;
  }>(),
  {
    alt: '',
    size: 48,
  },
);

const lightboxVisible = ref(false);
const src = computed(() => assetContentUrl(props.assetId));
const boxStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
}));

function openLightbox() {
  lightboxVisible.value = true;
}
</script>

<template>
  <button
    type="button"
    class="asset-thumb"
    :style="boxStyle"
    title="查看大图"
    @click.stop="openLightbox"
  >
    <img :src="src" :alt="alt || '素材预览'" />
  </button>
  <ImageLightbox v-model="lightboxVisible" :src="src" :alt="alt" />
</template>

<style scoped>
.asset-thumb {
  padding: 0;
  border: 0;
  background: #eef1f7;
  border-radius: 8px;
  overflow: hidden;
  cursor: zoom-in;
  display: inline-flex;
  flex-shrink: 0;
  vertical-align: middle;
}
.asset-thumb:focus-visible {
  outline: 2px solid #2f6fed;
  outline-offset: 2px;
}
.asset-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  pointer-events: none;
}
</style>
