<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();

const comboId = computed(() => String(route.params.id ?? ''));
const isNew = computed(() => comboId.value === 'new');

function backToList() {
  void router.push({ name: 'asset-combos' });
}
</script>

<template>
  <section class="combo-editor-page">
    <div class="header">
      <div>
        <p class="eyebrow">ASSETS</p>
        <h2>{{ isNew ? '新建组合' : `编辑组合 #${comboId}` }}</h2>
      </div>
      <el-button @click="backToList">返回列表</el-button>
    </div>
    <el-empty description="编辑器将在后续任务中完善" />
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
</style>
