<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { assetContentUrl } from '../../api/asset';

export interface PreviewMember {
  assetId: number;
  displayName: string;
  contentType: string | null;
}

export interface PreviewHold {
  stepIndex: number | null;
  holdSeconds: number | null;
}

const props = defineProps<{
  members: PreviewMember[];
  playSequence: string;
  defaultIntervalSec: number;
  loopEnabled: boolean;
  stepHolds: PreviewHold[];
}>();

const playing = ref(false);
/** True after loop-off reaches the end (last frame held). */
const finished = ref(false);
/** 0-based index into parsed steps[] */
const stepI = ref(0);
let timer: ReturnType<typeof setTimeout> | null = null;

function parseSteps(): number[] {
  if (props.members.length === 0) return [];
  const maxNo = props.members.length;
  const raw = props.playSequence.trim();
  if (!raw) {
    return Array.from({ length: maxNo }, (_, i) => i + 1);
  }
  const parts = raw.split(',').map((p) => p.trim()).filter(Boolean);
  const steps: number[] = [];
  for (const token of parts) {
    if (!/^\d+$/.test(token)) return [];
    const n = Number(token);
    if (n < 1 || n > maxNo) return [];
    steps.push(n);
  }
  return steps;
}

const steps = computed(() => parseSteps());
const total = computed(() => steps.value.length);
const canPlay = computed(() => total.value > 0 && props.defaultIntervalSec >= 0.1);

const holdByStepIndex = computed(() => {
  const map = new Map<number, number>();
  for (const row of props.stepHolds) {
    if (row.stepIndex == null || row.holdSeconds == null) continue;
    if (row.holdSeconds < 0.1) continue;
    map.set(row.stepIndex, row.holdSeconds);
  }
  return map;
});

function holdSecondsFor(i: number): number {
  return holdByStepIndex.value.get(i + 1) ?? props.defaultIntervalSec;
}

const currentHoldSec = computed(() => {
  if (!total.value) return props.defaultIntervalSec;
  const i = Math.min(stepI.value, total.value - 1);
  return holdSecondsFor(i);
});

const currentStepDisplay = computed(() => (total.value ? stepI.value + 1 : 0));

const currentMemberNo = computed(() => {
  if (!total.value) return null;
  return steps.value[Math.min(stepI.value, total.value - 1)] ?? null;
});

const currentMember = computed(() => {
  if (currentMemberNo.value == null) return null;
  return props.members[currentMemberNo.value - 1] ?? null;
});

const contentUrl = computed(() =>
  currentMember.value ? assetContentUrl(currentMember.value.assetId) : '',
);

function clearTimer() {
  if (timer != null) {
    clearTimeout(timer);
    timer = null;
  }
}

function scheduleNext() {
  clearTimer();
  if (!playing.value || !canPlay.value) return;

  const delayMs = Math.max(100, Math.round(holdSecondsFor(stepI.value) * 1000));
  timer = setTimeout(() => {
    if (!playing.value) return;
    const next = stepI.value + 1;
    if (next >= steps.value.length) {
      if (props.loopEnabled) {
        stepI.value = 0;
        scheduleNext();
      } else {
        stepI.value = Math.max(0, steps.value.length - 1);
        playing.value = false;
        finished.value = true;
        clearTimer();
      }
      return;
    }
    stepI.value = next;
    scheduleNext();
  }, delayMs);
}

function pause() {
  playing.value = false;
  clearTimer();
}

function play() {
  if (!canPlay.value) return;
  if (finished.value || stepI.value >= steps.value.length) {
    stepI.value = 0;
    finished.value = false;
  }
  playing.value = true;
  scheduleNext();
}

function togglePlay() {
  if (playing.value) pause();
  else play();
}

/** Reset to first step and start — “用当前表单预览”. */
function startFromForm(): boolean {
  pause();
  if (!canPlay.value) return false;
  finished.value = false;
  stepI.value = 0;
  playing.value = true;
  scheduleNext();
  return true;
}

watch(
  () => [
    props.playSequence,
    props.members.map((m) => m.assetId).join(','),
    props.defaultIntervalSec,
    props.loopEnabled,
    props.stepHolds.map((h) => `${h.stepIndex}:${h.holdSeconds}`).join('|'),
  ],
  () => {
    finished.value = false;
    if (!canPlay.value) {
      pause();
      stepI.value = 0;
      return;
    }
    if (stepI.value >= steps.value.length) {
      stepI.value = 0;
    }
    if (playing.value) {
      scheduleNext();
    }
  },
);

onUnmounted(() => {
  clearTimer();
});

defineExpose({ startFromForm, play, pause, togglePlay });
</script>

<template>
  <div class="combo-preview-player">
    <div class="preview-stage">
      <template v-if="currentMember && contentUrl">
        <img :src="contentUrl" :alt="currentMember.displayName" />
        <span class="member-badge" title="成员编号">{{ currentMemberNo }}</span>
      </template>
      <div v-else class="preview-empty">
        {{ canPlay ? '点击「用当前表单预览」开始' : '请完善成员与有效播放序列后再预览' }}
      </div>
    </div>

    <div class="preview-meta">
      <span>
        步 {{ currentStepDisplay }} / {{ total || '—' }}
      </span>
      <span>停留 {{ Number(currentHoldSec).toFixed(1) }} 秒</span>
      <span v-if="currentMember" class="muted">
        {{ currentMember.displayName }} (#{{ currentMember.assetId }})
      </span>
      <span class="muted">{{ loopEnabled ? '循环开' : '循环关' }}</span>
    </div>

    <div class="preview-controls">
      <el-button type="primary" :disabled="!canPlay" @click="togglePlay">
        {{ playing ? '暂停' : '播放' }}
      </el-button>
      <el-button :disabled="!canPlay" @click="startFromForm">从头播放</el-button>
    </div>
  </div>
</template>

<style scoped>
.combo-preview-player {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.preview-stage {
  position: relative;
  min-height: 280px;
  background: #0f1724;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.preview-stage img {
  max-width: 100%;
  max-height: 420px;
  object-fit: contain;
  display: block;
}
.member-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  min-width: 28px;
  height: 28px;
  padding: 0 8px;
  border-radius: 14px;
  background: rgba(31, 75, 122, 0.92);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.preview-empty {
  color: #9aa8bc;
  font-size: 14px;
  padding: 24px;
  text-align: center;
}
.preview-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
  font-size: 14px;
}
.preview-meta .muted {
  color: #7a8699;
}
.preview-controls {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
