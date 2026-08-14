<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();
const arcId = computed(() => route.params.arcId);
</script>

<template>
  <section class="page-list-page">
    <div class="header">
      <div>
        <p class="eyebrow">PAGES</p>
        <h2>页面列表</h2>
      </div>
      <el-button @click="router.back()">返回篇章</el-button>
    </div>
    <p class="hint">篇章 {{ arcId }} 的页面列表将在下一任务完善。</p>
  </section>
</template>

<style scoped>
.page-list-page {
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
  color: #6f7e9d;
  font-size: 12px;
  letter-spacing: 0.08em;
}
.header h2 {
  margin: 8px 0 0;
}
.hint {
  margin: 0;
  color: #6f7e9d;
  font-size: 14px;
}
</style>
