<script setup lang="ts">
import { computed, ref, watch, useSlots } from 'vue';

const props = defineProps<{
  modelValue: boolean;
  src: string | null;
  alt?: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const slots = useSlots();
const scale = ref(1);
const MIN_SCALE = 0.5;
const MAX_SCALE = 4;
const STEP = 0.12;

const visible = computed({
  get: () => props.modelValue && !!props.src,
  set: (v: boolean) => emit('update:modelValue', v),
});

watch(
  () => [props.modelValue, props.src] as const,
  () => {
    scale.value = 1;
  },
);

function close() {
  visible.value = false;
}

function onWheel(ev: WheelEvent) {
  ev.preventDefault();
  const delta = ev.deltaY > 0 ? -STEP : STEP;
  scale.value = Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale.value + delta));
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="visible && src"
      class="image-lightbox"
      role="dialog"
      aria-modal="true"
      @click="close"
      @wheel.prevent="onWheel"
    >
      <img
        class="image-lightbox-img"
        :src="src"
        :alt="alt || ''"
        :style="{ transform: `scale(${scale})` }"
        @click.stop
      />
      <div v-if="slots.chrome" class="image-lightbox-chrome" @click.stop>
        <slot name="chrome" />
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.image-lightbox {
  position: fixed;
  inset: 0;
  z-index: 4000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: rgba(12, 18, 32, 0.72);
  cursor: zoom-out;
  overflow: hidden;
}
.image-lightbox-img {
  max-width: min(860px, 92vw);
  max-height: 88vh;
  object-fit: contain;
  border-radius: 10px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  background: #111827;
  cursor: default;
  transform-origin: center center;
  transition: transform 0.05s linear;
  user-select: none;
  -webkit-user-drag: none;
}
.image-lightbox-chrome {
  position: absolute;
  left: 50%;
  bottom: 28px;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 10px;
  background: rgba(12, 18, 32, 0.82);
  color: #e8eefc;
  font-size: 13px;
  cursor: default;
}
</style>
