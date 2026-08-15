<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { assetContentUrl } from '../../api/asset';
import { arcReadingStreamUrl, getArc, type ArcItem } from '../../api/arc';
import { listPages, type PageItem } from '../../api/page';
import { getSeries } from '../../api/series';
import PagePreview, { type PagePreviewItem } from '../pages/PagePreview.vue';

const route = useRoute();
const router = useRouter();

const arcId = computed(() => Number(route.params.arcId));
const loading = ref(false);
const arc = ref<ArcItem | null>(null);
const pages = ref<PageItem[]>([]);
const seriesName = ref('');
const parsedByPageId = ref<Record<number, PagePreviewItem[]>>({});
const readingStreamDialogVisible = ref(false);
const coverLightboxVisible = ref(false);

const readingStreamFullUrl = computed(() => {
  const id = arcId.value;
  if (!Number.isFinite(id) || id <= 0) return '';
  return `${window.location.origin}${arcReadingStreamUrl(id)}`;
});

function apiError(e: unknown, fallback: string): string {
  const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
  return msg?.trim() || fallback;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return null;
}

function parseChild(node: unknown): { type: string; text?: string } {
  const obj = asRecord(node);
  return {
    type: obj?.type === 'DIALOGUE' ? 'DIALOGUE' : 'BODY',
    text: typeof obj?.text === 'string' ? obj.text : '',
  };
}

function parseTop(node: unknown): PagePreviewItem | null {
  const obj = asRecord(node);
  if (!obj) return null;
  const type = obj.type;
  if (type === 'BEAT') {
    const cover = obj.coverAssetId;
    const coverAssetId = typeof cover === 'number' && Number.isFinite(cover) ? cover : null;
    const childrenRaw = Array.isArray(obj.children) ? obj.children : [];
    return {
      type: 'BEAT',
      coverAssetId,
      children: childrenRaw.map(parseChild),
    };
  }
  if (type === 'DIVIDER') {
    return { type: 'DIVIDER' };
  }
  if (type === 'TITLE' || type === 'BODY') {
    return {
      type,
      text: typeof obj.text === 'string' ? obj.text : '',
    };
  }
  return null;
}

/** Slim parse aligned with PageEditor — outputs PagePreviewItem[] only. */
function parseContentJson(raw?: string | null): PagePreviewItem[] {
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
  return parsed.map(parseTop).filter((item): item is PagePreviewItem => item != null);
}

function headerTitle(): string {
  const arcTitle = arc.value?.title?.trim() || '篇章预览';
  if (seriesName.value) {
    return `${seriesName.value} · ${arcTitle}`;
  }
  return arcTitle;
}

function openReadingStreamDialog() {
  if (!readingStreamFullUrl.value) {
    ElMessage.warning('无效的篇章 ID');
    return;
  }
  readingStreamDialogVisible.value = true;
}

async function copyReadingStreamUrl() {
  const url = readingStreamFullUrl.value;
  if (!url) return;
  try {
    await navigator.clipboard.writeText(url);
    ElMessage.success('链接已复制');
  } catch {
    ElMessage.error('复制失败，请手动复制');
  }
}

function openCoverLightbox() {
  if (arc.value?.coverAssetId == null) return;
  coverLightboxVisible.value = true;
}

function closeCoverLightbox() {
  coverLightboxVisible.value = false;
}

function goBack() {
  const id = arcId.value;
  if (route.query.from === 'pages' && Number.isFinite(id) && id > 0) {
    void router.push(`/arcs/${id}/pages`);
    return;
  }
  const seriesId = arc.value?.seriesId;
  if (seriesId != null && seriesId > 0) {
    void router.push(`/series/${seriesId}/arcs`);
    return;
  }
  void router.back();
}

async function load() {
  const id = arcId.value;
  if (!Number.isFinite(id) || id <= 0) {
    arc.value = null;
    pages.value = [];
    seriesName.value = '';
    parsedByPageId.value = {};
    return;
  }
  loading.value = true;
  try {
    const [arcData, pageList] = await Promise.all([getArc(id), listPages(id)]);
    arc.value = arcData;
    pages.value = pageList;

    const map: Record<number, PagePreviewItem[]> = {};
    for (const page of pageList) {
      if (page.id == null) continue;
      map[page.id] = parseContentJson(page.contentJson);
    }
    parsedByPageId.value = map;

    if (arcData.seriesId != null && arcData.seriesId > 0) {
      try {
        const series = await getSeries(arcData.seriesId);
        seriesName.value = series.name ?? '';
      } catch {
        seriesName.value = '';
      }
    } else {
      seriesName.value = '';
    }
  } catch (e: unknown) {
    arc.value = null;
    pages.value = [];
    seriesName.value = '';
    parsedByPageId.value = {};
    ElMessage.error(apiError(e, '加载篇章预览失败'));
  } finally {
    loading.value = false;
  }
}

watch(arcId, () => {
  void load();
});

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="arc-preview-page">
    <header class="header">
      <div class="header-main">
        <el-button @click="goBack">返回</el-button>
        <h2>{{ headerTitle() }}</h2>
      </div>
      <el-button type="primary" plain :disabled="!arcId || arcId <= 0" @click="openReadingStreamDialog">
        AI 阅读流
      </el-button>
    </header>

    <el-dialog v-model="readingStreamDialogVisible" title="AI 阅读流" width="560px">
      <p class="dialog-label">接口 URL</p>
      <el-input :model-value="readingStreamFullUrl" readonly type="textarea" :rows="2" />
      <ul class="usage-list">
        <li>需携带登录 Session（Cookie）访问，未登录返回 401。</li>
        <li>按响应中 <code>segments</code> 数组顺序依次处理各段内容。</li>
        <li>有 <code>text</code> 字段则读文本；<code>IMAGE</code> / <code>ARC_COVER</code> 等用登录态请求 <code>contentPath</code> 获取图片。</li>
      </ul>
      <template #footer>
        <el-button @click="readingStreamDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="copyReadingStreamUrl">复制链接</el-button>
      </template>
    </el-dialog>

    <div v-loading="loading" class="reader">
      <button
        v-if="arc?.coverAssetId != null"
        type="button"
        class="arc-cover-btn"
        title="查看大图"
        @click="openCoverLightbox"
      >
        <img
          class="arc-cover"
          :src="assetContentUrl(arc.coverAssetId)"
          :alt="arc.title"
        />
      </button>
      <h1 v-if="arc" class="arc-title">{{ arc.title }}</h1>
      <p v-if="arc?.summary" class="summary">{{ arc.summary }}</p>
      <p v-if="!loading && !pages.length" class="empty">本篇章暂无页面</p>
      <template v-for="page in pages" :key="page.id">
        <h2 class="page-title">{{ page.title }}</h2>
        <PagePreview :items="page.id != null ? parsedByPageId[page.id] ?? [] : []" />
      </template>
    </div>

    <Teleport to="body">
      <div
        v-if="coverLightboxVisible && arc?.coverAssetId != null"
        class="cover-lightbox"
        role="dialog"
        aria-modal="true"
        @click="closeCoverLightbox"
      >
        <img
          class="cover-lightbox-img"
          :src="assetContentUrl(arc.coverAssetId)"
          :alt="arc.title"
          @click.stop
        />
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.arc-preview-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.header-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1f2a44;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.reader {
  max-width: 720px;
  margin: 0 auto;
  width: 100%;
  padding: 8px 12px 48px;
  color: #1f2a44;
  line-height: 1.75;
  min-height: 200px;
}
.arc-cover-btn {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: zoom-in;
  border-radius: 12px;
  margin-bottom: 28px;
}
.arc-cover-btn:focus-visible {
  outline: 2px solid #2f6fed;
  outline-offset: 2px;
}
.arc-cover {
  width: 100%;
  display: block;
  border-radius: 12px;
  background: #eef1f7;
  object-fit: cover;
  max-height: 360px;
}
.cover-lightbox {
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
.cover-lightbox-img {
  max-width: min(860px, 92vw);
  max-height: 88vh;
  object-fit: contain;
  border-radius: 10px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  background: #111827;
  cursor: default;
}
.arc-title {
  margin: 0 0 12px;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.35;
}
.summary {
  margin: 0 0 36px;
  font-size: 15px;
  color: #4a5878;
  white-space: pre-wrap;
  word-break: break-word;
}
.empty {
  margin: 48px 0;
  color: #9aa6bf;
  text-align: center;
  font-size: 14px;
}
.page-title {
  margin: 44px 0 20px;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.4;
  padding-bottom: 10px;
  border-bottom: 1px solid #d8deea;
}
.page-title:first-of-type {
  margin-top: 28px;
}
.dialog-label {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #4a5878;
}
.usage-list {
  margin: 16px 0 0;
  padding-left: 20px;
  font-size: 13px;
  color: #4a5878;
  line-height: 1.65;
}
.usage-list code {
  font-size: 12px;
  background: #eef1f7;
  padding: 1px 4px;
  border-radius: 4px;
}
</style>
