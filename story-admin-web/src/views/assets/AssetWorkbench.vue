<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import draggable from 'vuedraggable';
import {
  assetContentUrl,
  batchLinkAssets,
  listAssets,
  moveAsset,
  recycleAsset,
  reorderAssets,
  reorderAssetsByScope,
  replaceAssetContent,
  updateAsset,
  uploadAssets,
  type AssetItem,
  type AssetLinkType,
  type AssetUpdatePayload,
} from '../../api/asset';
import { useRouter } from 'vue-router';
import { listCategories, type AssetCategoryItem } from '../../api/category';
import { listCharacters, type CharacterItem } from '../../api/character';
import { listSeries, type SeriesItem } from '../../api/series';
import { getArc, listArcs, type ArcItem } from '../../api/arc';
import ImageLightbox from '../../components/ImageLightbox.vue';

type LinkTypeFilter = '' | AssetLinkType;

const router = useRouter();
const loading = ref(false);
const uploading = ref(false);
const saving = ref(false);
const recycling = ref(false);
const replacing = ref(false);
const previewBust = ref(0);
const lightboxVisible = ref(false);
const checkedIds = ref<number[]>([]);
const batchDialogVisible = ref(false);
const batchSaving = ref(false);
const batchForm = reactive({
  linkType: 'NONE' as AssetLinkType,
  seriesIds: [] as number[],
  arcIds: [] as number[],
  characterIds: [] as number[],
});
const batchSeriesForArc = ref<number | ''>('');
const batchArcs = ref<ArcItem[]>([]);
const replaceFileInput = ref<HTMLInputElement | null>(null);
const categories = ref<AssetCategoryItem[]>([]);
const assets = ref<AssetItem[]>([]);
const seriesList = ref<SeriesItem[]>([]);
const filterArcs = ref<ArcItem[]>([]);
const formArcs = ref<ArcItem[]>([]);
const selectedCategoryId = ref<number | null>(null);
const selectedAssetId = ref<number | null>(null);
const search = ref('');
const linkTypeFilter = ref<LinkTypeFilter>('');
const filterSeriesId = ref<number | ''>('');
const filterArcId = ref<number | ''>('');
/** 无关联 | 具体人物 id | 全部（默认全部） */
const characterFilter = ref<'unlinked' | 'all' | number>('all');
const indexInput = ref('1');
const fileInput = ref<HTMLInputElement | null>(null);
const thumbsScroller = ref<HTMLElement | null>(null);
const categoryBuckets = reactive<Record<number, AssetItem[]>>({});
const dragging = ref(false);
const thumbGroup = { name: 'assets', pull: true, put: false };
const categoryGroup = { name: 'assets', pull: false, put: true };
const seriesForArc = ref<number | ''>('');
let syncingForm = false;
let syncGen = 0;
let thumbEdgeDir = 0;
let thumbAutoScrollRaf = 0;

const THUMB_EDGE_ZONE = 56;
const THUMB_SCROLL_SPEED = 6;

type DragChangeEvent = {
  added?: { element: AssetItem; newIndex: number };
  removed?: { element: AssetItem; oldIndex: number };
  moved?: { element: AssetItem; oldIndex: number; newIndex: number };
};

let dragSnapshot: AssetItem[] = [];
let dragEndedAt = 0;

function bucketFor(categoryId: number): AssetItem[] {
  if (!categoryBuckets[categoryId]) {
    categoryBuckets[categoryId] = [];
  }
  return categoryBuckets[categoryId];
}

function clearBuckets() {
  for (const key of Object.keys(categoryBuckets)) {
    const bucket = categoryBuckets[Number(key)];
    bucket.splice(0, bucket.length);
  }
}

function restoreDragSnapshot() {
  assets.value = dragSnapshot.map((item) => ({ ...item }));
  clearBuckets();
}

function onThumbPointerMove(ev: DragEvent | PointerEvent | MouseEvent) {
  const el = thumbsScroller.value;
  if (!el || !dragging.value) return;
  const x = ev.clientX;
  const rect = el.getBoundingClientRect();
  if (x < rect.left + THUMB_EDGE_ZONE) {
    thumbEdgeDir = -1;
  } else if (x > rect.right - THUMB_EDGE_ZONE) {
    thumbEdgeDir = 1;
  } else {
    thumbEdgeDir = 0;
  }
}

function tickThumbAutoScroll() {
  const el = thumbsScroller.value;
  if (!el || !dragging.value) {
    thumbAutoScrollRaf = 0;
    thumbEdgeDir = 0;
    return;
  }
  if (thumbEdgeDir !== 0) {
    el.scrollLeft += thumbEdgeDir * THUMB_SCROLL_SPEED;
  }
  thumbAutoScrollRaf = requestAnimationFrame(tickThumbAutoScroll);
}

function startThumbAutoScroll() {
  document.addEventListener('dragover', onThumbPointerMove);
  document.addEventListener('pointermove', onThumbPointerMove);
  if (!thumbAutoScrollRaf) {
    thumbAutoScrollRaf = requestAnimationFrame(tickThumbAutoScroll);
  }
}

function stopThumbAutoScroll() {
  document.removeEventListener('dragover', onThumbPointerMove);
  document.removeEventListener('pointermove', onThumbPointerMove);
  if (thumbAutoScrollRaf) {
    cancelAnimationFrame(thumbAutoScrollRaf);
    thumbAutoScrollRaf = 0;
  }
  thumbEdgeDir = 0;
}

function onDragStart() {
  dragging.value = true;
  dragSnapshot = assets.value.map((item) => ({ ...item }));
  startThumbAutoScroll();
}

function onDragEnd() {
  dragging.value = false;
  dragEndedAt = Date.now();
  stopThumbAutoScroll();
}

async function onThumbsChange(evt: DragChangeEvent) {
  if (!evt.moved || selectedCategoryId.value == null) return;
  if (hasKeyword.value) {
    // 搜索中仅内存顺序，不调 API
    return;
  }
  const scope = persistSortScope.value;
  if (scope === 'none') {
    restoreDragSnapshot();
    return;
  }
  await nextTick();
  try {
    const orderedIds = assets.value.map((item) => item.id);
    if (scope == null) {
      await reorderAssets({
        categoryId: selectedCategoryId.value,
        orderedIds,
      });
    } else {
      await reorderAssetsByScope({
        categoryId: selectedCategoryId.value,
        scope: scope.scope,
        scopeId: scope.scopeId,
        orderedIds,
      });
    }
    await loadAssets(selectedAssetId.value);
  } catch (e) {
    restoreDragSnapshot();
    ElMessage.error(apiError(e, '排序失败'));
  }
}

async function onDropOnCategory(categoryId: number, evt: DragChangeEvent) {
  if (!evt.added) return;
  const item = evt.added.element;
  const bucket = bucketFor(categoryId);
  bucket.splice(0, bucket.length);
  if (categoryId === selectedCategoryId.value) {
    restoreDragSnapshot();
    return;
  }
  try {
    await moveAsset(item.id, {
      targetCategoryId: categoryId,
      targetIndex: evt.added.newIndex ?? 0,
    });
    ElMessage.success('已移动到目标分类');
    const keepId = selectedAssetId.value === item.id ? null : selectedAssetId.value;
    await loadAssets(keepId);
  } catch (e) {
    restoreDragSnapshot();
    ElMessage.error(apiError(e, '移动失败'));
  }
}

const characters = ref<CharacterItem[]>([]);

const form = reactive({
  displayName: '',
  description: '',
  chapterRefPlaceholder: '',
  tagNames: [] as string[],
  linkType: 'NONE' as AssetLinkType,
  seriesIds: [] as number[],
  arcIds: [] as number[],
  characterIds: [] as number[],
});

const selectedCategory = computed(
  () => categories.value.find((c) => c.id === selectedCategoryId.value) ?? null,
);
const currentIndex = computed(() =>
  assets.value.findIndex((a) => a.id === selectedAssetId.value),
);
const currentAsset = computed(() =>
  currentIndex.value >= 0 ? assets.value[currentIndex.value] : null,
);
const canPrev = computed(() => currentIndex.value > 0);
const canNext = computed(
  () => currentIndex.value >= 0 && currentIndex.value < assets.value.length - 1,
);
const showSeriesFilter = computed(
  () => linkTypeFilter.value === 'SERIES' || linkTypeFilter.value === 'ARC',
);
const showArcFilter = computed(() => linkTypeFilter.value === 'ARC');
const showCharacterFilter = computed(
  () => linkTypeFilter.value === '' || linkTypeFilter.value === 'CHARACTER',
);
const hasKeyword = computed(() => search.value.trim().length > 0);

type PersistSortScope =
  | null
  | 'none'
  | { scope: 'CHARACTER' | 'SERIES' | 'ARC' | 'UNLINKED'; scopeId?: number };

function asFilterId(value: number | '' | string | null | undefined): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim() !== '') {
    const n = Number(value);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

/** 可持久化的 scope；null = 用分类全局 reorder；'none' = 禁止持久化改序 */
const persistSortScope = computed((): PersistSortScope => {
  // NONE 筛选集 ≠ 服务端 UNLINKED/CHARACTER 集合，禁止持久化改序
  if (linkTypeFilter.value === 'NONE') {
    return 'none';
  }
  if (linkTypeFilter.value === 'SERIES') {
    const sid = asFilterId(filterSeriesId.value);
    if (sid != null) {
      return { scope: 'SERIES', scopeId: sid };
    }
    return 'none';
  }
  if (linkTypeFilter.value === 'ARC') {
    const aid = asFilterId(filterArcId.value);
    if (aid != null) {
      return { scope: 'ARC', scopeId: aid };
    }
    return 'none';
  }
  if (typeof characterFilter.value === 'number') {
    return { scope: 'CHARACTER', scopeId: characterFilter.value };
  }
  if (characterFilter.value === 'unlinked') {
    return { scope: 'UNLINKED' };
  }
  if (characterFilter.value === 'all' && linkTypeFilter.value === '') {
    return null;
  }
  if (characterFilter.value === 'all' && linkTypeFilter.value === 'CHARACTER') {
    return 'none';
  }
  return 'none';
});

const canSortThumbs = computed(() => {
  if (hasKeyword.value) return true;
  return persistSortScope.value !== 'none';
});

const reorderHint = computed(() => {
  if (hasKeyword.value) {
    return '搜索中顺序仅临时，刷新后恢复；仍可拖到左侧其它分类';
  }
  const scope = persistSortScope.value;
  if (scope === 'none') {
    return '当前筛选未选具体目标，本分类内排序暂不可用，仍可拖到左侧其它分类';
  }
  if (scope == null) return '';
  if (scope.scope === 'CHARACTER') {
    return '当前按人物顺序排列，拖拽将保存到该人物';
  }
  if (scope.scope === 'SERIES' || scope.scope === 'ARC') {
    return '当前按系列/篇章顺序排列，拖拽将保存到该筛选';
  }
  if (scope.scope === 'UNLINKED') {
    return '当前按「无关联」顺序排列，拖拽将保存到本分类无关联视图';
  }
  return '';
});

function apiError(e: unknown, fallback: string): string {
  const err = e as { response?: { data?: { message?: string } } };
  return err?.response?.data?.message || fallback;
}

async function loadCategories(preferId?: number | null) {
  categories.value = await listCategories();
  for (const cat of categories.value) {
    bucketFor(cat.id);
  }
  const keep =
    preferId != null && categories.value.some((c) => c.id === preferId)
      ? preferId
      : selectedCategoryId.value != null &&
          categories.value.some((c) => c.id === selectedCategoryId.value)
        ? selectedCategoryId.value
        : (categories.value[0]?.id ?? null);
  selectedCategoryId.value = keep;
}

async function loadFilterArcs(seriesId: number) {
  try {
    filterArcs.value = await listArcs(seriesId);
  } catch (e) {
    filterArcs.value = [];
    ElMessage.error(apiError(e, '加载篇章失败'));
  }
}

async function loadFormArcs(seriesId: number) {
  try {
    formArcs.value = await listArcs(seriesId);
  } catch (e) {
    formArcs.value = [];
    ElMessage.error(apiError(e, '加载篇章失败'));
  }
}

async function loadAssets(keepId?: number | null) {
  if (selectedCategoryId.value == null) {
    assets.value = [];
    selectedAssetId.value = null;
    return;
  }
  loading.value = true;
  try {
    const listParams: Parameters<typeof listAssets>[0] = {
      categoryId: selectedCategoryId.value,
      status: 'NORMAL',
      q: search.value.trim() || undefined,
    };
    if (linkTypeFilter.value) {
      listParams.linkType = linkTypeFilter.value;
    }
    const seriesId = asFilterId(filterSeriesId.value);
    const arcId = asFilterId(filterArcId.value);
    if (
      (linkTypeFilter.value === 'SERIES' || linkTypeFilter.value === 'ARC') &&
      seriesId != null
    ) {
      listParams.seriesId = seriesId;
    }
    if (linkTypeFilter.value === 'ARC' && arcId != null) {
      listParams.arcId = arcId;
    }
    if (showCharacterFilter.value) {
      if (typeof characterFilter.value === 'number') {
        listParams.characterId = characterFilter.value;
      } else if (characterFilter.value === 'unlinked' || characterFilter.value === 'all') {
        listParams.characterFilter = characterFilter.value;
      }
    }
    assets.value = await listAssets(listParams);
    const preferred = keepId ?? selectedAssetId.value;
    if (preferred != null && assets.value.some((a) => a.id === preferred)) {
      selectedAssetId.value = preferred;
    } else {
      selectedAssetId.value = assets.value[0]?.id ?? null;
    }
  } catch (e) {
    ElMessage.error(apiError(e, '加载素材失败'));
  } finally {
    loading.value = false;
  }
}

async function ensureSeriesForArc(arcId: number): Promise<void> {
  const known =
    formArcs.value.find((a) => a.id === arcId) ?? filterArcs.value.find((a) => a.id === arcId);
  let sid = known?.seriesId ?? null;
  if (sid == null) {
    try {
      const arc = await getArc(arcId);
      sid = arc.seriesId ?? null;
    } catch (e) {
      ElMessage.error(apiError(e, '加载篇章失败'));
      return;
    }
  }
  if (sid == null) return;
  seriesForArc.value = sid;
  await loadFormArcs(sid);
}

async function syncForm() {
  const gen = ++syncGen;
  const asset = currentAsset.value;
  syncingForm = true;
  try {
    form.displayName = asset?.displayName ?? '';
    form.description = asset?.description ?? '';
    form.chapterRefPlaceholder = asset?.chapterRefPlaceholder ?? '';
    form.tagNames = [...(asset?.tagNames ?? [])];
    form.linkType = asset?.linkType ?? 'NONE';
    form.seriesIds = [...(asset?.seriesIds ?? [])];
    form.arcIds = [...(asset?.arcIds ?? [])];
    form.characterIds = [...(asset?.characterIds ?? [])];
    indexInput.value = currentIndex.value >= 0 ? String(currentIndex.value + 1) : '';
    if (form.linkType === 'ARC' && form.arcIds.length) {
      await ensureSeriesForArc(form.arcIds[0]);
    } else {
      seriesForArc.value = '';
      formArcs.value = [];
    }
  } finally {
    if (gen === syncGen) {
      syncingForm = false;
    }
  }
}

function onFormLinkTypeChange() {
  if (syncingForm) return;
  form.seriesIds = [];
  form.arcIds = [];
  form.characterIds = [];
  seriesForArc.value = '';
  formArcs.value = [];
  ElMessage.info('已切换关联类型，请重新选择');
}

async function onFormSeriesForArcChange(id: number | '') {
  form.arcIds = [];
  if (typeof id === 'number') {
    await loadFormArcs(id);
  } else {
    formArcs.value = [];
  }
}

watch(currentAsset, () => {
  void syncForm();
}, { immediate: true });

watch(selectedAssetId, async () => {
  await nextTick();
  const scroller = thumbsScroller.value;
  const el = document.querySelector<HTMLElement>(`[data-thumb-id="${selectedAssetId.value}"]`);
  if (!scroller || !el) return;
  const scrollerRect = scroller.getBoundingClientRect();
  const elRect = el.getBoundingClientRect();
  const delta =
    elRect.left + elRect.width / 2 - (scrollerRect.left + scrollerRect.width / 2);
  scroller.scrollLeft += delta;
});

async function selectCategory(id: number) {
  if (Date.now() - dragEndedAt < 300) return;
  selectedCategoryId.value = id;
  clearChecked();
  await loadAssets();
}

function triggerUpload() {
  if (selectedCategoryId.value == null) {
    ElMessage.warning('请先选择分类');
    return;
  }
  fileInput.value?.click();
}

function uploadLinkFromFilters(): {
  linkType: AssetLinkType;
  seriesIds?: number[];
  arcIds?: number[];
  characterIds?: number[];
} | undefined {
  if (linkTypeFilter.value === 'CHARACTER' && typeof characterFilter.value === 'number') {
    return { linkType: 'CHARACTER', characterIds: [characterFilter.value] };
  }
  const seriesId = asFilterId(filterSeriesId.value);
  const arcId = asFilterId(filterArcId.value);
  if (linkTypeFilter.value === 'SERIES' && seriesId != null) {
    return { linkType: 'SERIES', seriesIds: [seriesId] };
  }
  if (linkTypeFilter.value === 'ARC' && arcId != null) {
    return { linkType: 'ARC', arcIds: [arcId] };
  }
  return undefined;
}

async function onFilesPicked(ev: Event) {
  const input = ev.target as HTMLInputElement;
  const files = input.files ? Array.from(input.files) : [];
  input.value = '';
  if (!files.length || selectedCategoryId.value == null) return;
  uploading.value = true;
  try {
    const link = uploadLinkFromFilters();
    const uploaded = await uploadAssets(selectedCategoryId.value, files, link);
    ElMessage.success(
      link
        ? `已上传 ${uploaded.length} 份素材，并按当前筛选自动关联`
        : `已上传 ${uploaded.length} 份素材`,
    );
    await loadAssets(uploaded[0]?.id ?? null);
  } catch (e) {
    ElMessage.error(apiError(e, '上传失败'));
  } finally {
    uploading.value = false;
  }
}

function selectAsset(id: number) {
  selectedAssetId.value = id;
}

function isChecked(id: number): boolean {
  return checkedIds.value.includes(id);
}

function toggleChecked(id: number, ev?: Event) {
  ev?.stopPropagation();
  if (isChecked(id)) {
    checkedIds.value = checkedIds.value.filter((x) => x !== id);
  } else {
    checkedIds.value = [...checkedIds.value, id];
  }
}

function clearChecked() {
  checkedIds.value = [];
}

function openBatchLinkDialog() {
  if (!checkedIds.value.length) {
    ElMessage.warning('请先勾选素材');
    return;
  }
  batchForm.linkType = 'NONE';
  batchForm.seriesIds = [];
  batchForm.arcIds = [];
  batchForm.characterIds = [];
  batchSeriesForArc.value = '';
  batchArcs.value = [];
  batchDialogVisible.value = true;
}

function onBatchLinkTypeChange() {
  batchForm.seriesIds = [];
  batchForm.arcIds = [];
  batchForm.characterIds = [];
  batchSeriesForArc.value = '';
  batchArcs.value = [];
}

async function onBatchSeriesForArcChange(id: number | '') {
  batchForm.arcIds = [];
  if (typeof id === 'number') {
    try {
      batchArcs.value = await listArcs(id);
    } catch {
      batchArcs.value = [];
    }
  } else {
    batchArcs.value = [];
  }
}

async function confirmBatchLink() {
  if (!checkedIds.value.length) return;
  if (batchForm.linkType === 'SERIES' && !batchForm.seriesIds.length) {
    ElMessage.warning('请选择系列');
    return;
  }
  if (batchForm.linkType === 'ARC' && !batchForm.arcIds.length) {
    ElMessage.warning('请选择篇章');
    return;
  }
  if (batchForm.linkType === 'CHARACTER' && !batchForm.characterIds.length) {
    ElMessage.warning('请选择人物');
    return;
  }
  batchSaving.value = true;
  try {
    const updated = await batchLinkAssets({
      assetIds: [...checkedIds.value],
      linkType: batchForm.linkType,
      seriesIds: batchForm.linkType === 'SERIES' ? [...batchForm.seriesIds] : [],
      arcIds: batchForm.linkType === 'ARC' ? [...batchForm.arcIds] : [],
      characterIds: batchForm.linkType === 'CHARACTER' ? [...batchForm.characterIds] : [],
    });
    ElMessage.success(`已关联 ${updated.length} 项`);
    batchDialogVisible.value = false;
    clearChecked();
    await loadAssets(selectedAssetId.value);
  } catch (e) {
    ElMessage.error(apiError(e, '批量关联失败'));
  } finally {
    batchSaving.value = false;
  }
}

function openLightbox() {
  if (!currentAsset.value) return;
  lightboxVisible.value = true;
}

function goPrev() {
  if (!canPrev.value) return;
  selectedAssetId.value = assets.value[currentIndex.value - 1].id;
}

function goNext() {
  if (!canNext.value) return;
  selectedAssetId.value = assets.value[currentIndex.value + 1].id;
}

function jumpToIndex() {
  const n = Number.parseInt(indexInput.value, 10);
  if (!Number.isInteger(n) || n < 1 || n > assets.value.length) {
    ElMessage.warning(
      assets.value.length === 0 ? '当前没有可预览的素材' : `请输入 1 到 ${assets.value.length} 的序号`,
    );
    indexInput.value = currentIndex.value >= 0 ? String(currentIndex.value + 1) : '';
    return;
  }
  selectedAssetId.value = assets.value[n - 1].id;
}

async function saveMeta() {
  if (!currentAsset.value) return;
  if (!form.displayName.trim()) {
    ElMessage.warning('请填写显示名称');
    return;
  }
  if (form.linkType === 'SERIES' && !form.seriesIds.length) {
    ElMessage.warning('请选择关联系列');
    return;
  }
  if (form.linkType === 'ARC' && !form.arcIds.length) {
    ElMessage.warning('请选择关联篇章');
    return;
  }
  if (form.linkType === 'CHARACTER' && !form.characterIds.length) {
    ElMessage.warning('请选择关联人物');
    return;
  }
  saving.value = true;
  try {
    const payload: AssetUpdatePayload = {
      displayName: form.displayName.trim(),
      description: form.description.trim() || null,
      chapterRefPlaceholder: form.chapterRefPlaceholder.trim() || null,
      tagNames: form.tagNames.map((t) => t.trim()).filter(Boolean),
      linkType: form.linkType,
      seriesIds: form.linkType === 'SERIES' ? [...form.seriesIds] : [],
      arcIds: form.linkType === 'ARC' ? [...form.arcIds] : [],
      characterIds: form.linkType === 'CHARACTER' ? [...form.characterIds] : [],
    };
    const updated = await updateAsset(currentAsset.value.id, payload);
    ElMessage.success('已保存');
    await loadAssets(updated.id);
  } catch (e) {
    ElMessage.error(apiError(e, '保存失败'));
  } finally {
    saving.value = false;
  }
}

async function moveToRecycle() {
  if (!currentAsset.value) return;
  try {
    await ElMessageBox.confirm(
      `确认将「${currentAsset.value.displayName}」移入回收站？`,
      '移入回收站',
      { type: 'warning' },
    );
    recycling.value = true;
    await recycleAsset(currentAsset.value.id);
    ElMessage.success('已移入回收站');
    await loadAssets(null);
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return;
    ElMessage.error(apiError(e, '移入回收站失败'));
  } finally {
    recycling.value = false;
  }
}

function triggerReplace() {
  if (!currentAsset.value) return;
  if (currentAsset.value.status !== 'NORMAL') {
    ElMessage.warning('仅正常状态的素材可替换图片');
    return;
  }
  replaceFileInput.value?.click();
}

async function onReplaceFilePicked(ev: Event) {
  const input = ev.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || !currentAsset.value) return;
  replacing.value = true;
  try {
    const updated = await replaceAssetContent(currentAsset.value.id, file);
    previewBust.value = Date.now();
    ElMessage.success('图片已替换');
    await loadAssets(updated.id);
  } catch (e) {
    ElMessage.error(apiError(e, '替换失败'));
  } finally {
    replacing.value = false;
  }
}

function formatSize(bytes: number | null | undefined): string {
  if (bytes == null) return '-';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

let searchTimer: ReturnType<typeof setTimeout> | null = null;
watch(search, () => {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    void loadAssets();
  }, 250);
});

watch(linkTypeFilter, async (next) => {
  if (next === 'CHARACTER' && characterFilter.value === 'unlinked') {
    characterFilter.value = 'all';
  }
  if (next !== 'ARC') {
    filterArcId.value = '';
    filterArcs.value = [];
  } else if (typeof filterSeriesId.value === 'number') {
    await loadFilterArcs(filterSeriesId.value);
  }
});

watch(filterSeriesId, async (sid) => {
  filterArcId.value = '';
  if (linkTypeFilter.value === 'ARC' && typeof sid === 'number') {
    await loadFilterArcs(sid);
  } else if (linkTypeFilter.value !== 'ARC') {
    filterArcs.value = [];
  }
});

watch([linkTypeFilter, filterSeriesId, filterArcId, characterFilter], () => {
  void loadAssets();
});

onMounted(async () => {
  try {
    const [chars, seriesRows] = await Promise.all([
      listCharacters(),
      listSeries(),
      loadCategories(),
    ]);
    characters.value = chars;
    seriesList.value = seriesRows;
    await loadAssets();
  } catch (e) {
    ElMessage.error(apiError(e, '加载分类失败'));
  }
});

onUnmounted(() => {
  stopThumbAutoScroll();
});
</script>

<template>
  <section class="workbench">
    <header class="toolbar">
      <div>
        <p class="eyebrow">ASSETS</p>
        <h2>素材管理</h2>
      </div>
      <div class="toolbar-actions">
        <el-select
          v-model="linkTypeFilter"
          placeholder="关联类型"
          style="width: 128px"
        >
          <el-option label="全部" value="" />
          <el-option label="无" value="NONE" />
          <el-option label="系列" value="SERIES" />
          <el-option label="篇章" value="ARC" />
          <el-option label="人物" value="CHARACTER" />
        </el-select>
        <el-select
          v-if="showSeriesFilter"
          v-model="filterSeriesId"
          clearable
          placeholder="选择系列"
          style="width: 160px"
        >
          <el-option
            v-for="s in seriesList"
            :key="s.id"
            :label="s.name"
            :value="s.id!"
          />
        </el-select>
        <el-select
          v-if="showArcFilter"
          v-model="filterArcId"
          clearable
          placeholder="选择篇章"
          :disabled="typeof filterSeriesId !== 'number'"
          style="width: 160px"
        >
          <el-option
            v-for="a in filterArcs"
            :key="a.id"
            :label="a.title"
            :value="a.id!"
          />
        </el-select>
        <el-select
          v-if="showCharacterFilter"
          v-model="characterFilter"
          placeholder="人物筛选"
          style="width: 160px"
        >
          <el-option
            v-if="linkTypeFilter === ''"
            label="无关联（人物/系列/篇章均无）"
            value="unlinked"
          />
          <el-option
            v-for="c in characters"
            :key="c.id"
            :label="c.name"
            :value="c.id!"
          />
          <el-option label="全部" value="all" />
        </el-select>
        <el-input
          v-model="search"
          clearable
          placeholder="搜索显示名 / 文件名 / 说明"
          style="width: 240px"
        />
        <input
          ref="fileInput"
          type="file"
          multiple
          accept=".jpg,.jpeg,.png,.webp,.gif,image/jpeg,image/png,image/webp,image/gif"
          hidden
          @change="onFilesPicked"
        />
        <el-button @click="router.push('/recycle')">回收站</el-button>
        <el-button :disabled="!checkedIds.length" @click="clearChecked">清空选择</el-button>
        <el-button type="success" plain :disabled="!checkedIds.length" @click="openBatchLinkDialog">
          批量关联{{ checkedIds.length ? ` (${checkedIds.length})` : '' }}
        </el-button>
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="selectedCategoryId == null"
          @click="triggerUpload"
        >
          上传
        </el-button>
      </div>
    </header>

    <div class="layout">
      <div class="pane categories">
        <div class="pane-head">
          <strong>分类</strong>
          <el-button link type="primary" @click="router.push('/assets/categories')">去配置</el-button>
        </div>
        <ul>
          <li v-for="cat in categories" :key="cat.id">
            <div
              class="category-drop-wrap"
              :class="{ active: cat.id === selectedCategoryId, 'drop-ready': dragging }"
            >
              <button
                type="button"
                class="cat-row"
                @click="selectCategory(cat.id)"
              >
                <span class="cat-name">{{ cat.name }}</span>
                <span v-if="dragging && cat.id !== selectedCategoryId" class="drop-hint">放到此处</span>
              </button>
              <draggable
                :list="bucketFor(cat.id)"
                item-key="id"
                :group="categoryGroup"
                :animation="150"
                :empty-insert-threshold="64"
                class="category-drop-zone"
                @change="onDropOnCategory(cat.id, $event)"
              >
                <template #item="{ element }">
                  <span class="drop-chip">{{ element.displayName }}</span>
                </template>
              </draggable>
            </div>
          </li>
        </ul>
        <p v-if="!categories.length" class="empty">
          暂无分类，请先到
          <el-button link type="primary" @click="router.push('/assets/categories')">管理配置</el-button>
          新增
        </p>
      </div>

      <section v-loading="loading" class="pane preview">
        <div v-if="currentAsset" class="preview-main">
          <button
            type="button"
            class="preview-img-btn"
            title="查看大图"
            @click="openLightbox"
          >
            <img
              :src="assetContentUrl(currentAsset.id, previewBust || undefined)"
              :alt="currentAsset.displayName"
            />
          </button>
          <div class="preview-meta">
            <strong>{{ currentAsset.displayName }}</strong>
            <span>第 {{ currentIndex + 1 }} / {{ assets.length }} 份</span>
          </div>
          <div class="nav">
            <el-button :disabled="!canPrev" @click="goPrev">上一份</el-button>
            <el-input
              v-model="indexInput"
              style="width: 88px"
              @keyup.enter="jumpToIndex"
              @blur="jumpToIndex"
            />
            <el-button @click="jumpToIndex">跳转</el-button>
            <el-button :disabled="!canNext" @click="goNext">下一份</el-button>
          </div>
        </div>
        <div v-else class="empty-preview">当前分类暂无素材，请先上传</div>
        <p class="drag-tip">
          将下方缩略图拖到左侧其它分类，即可更换分类；同分类内拖拽可按当前筛选范围排序并保存。
        </p>
        <p v-if="reorderHint" class="search-reorder-hint">
          {{ reorderHint }}
        </p>
        <div ref="thumbsScroller" class="thumbs">
          <draggable
            v-model="assets"
            item-key="id"
            :group="thumbGroup"
            :sort="canSortThumbs"
            :animation="150"
            :scroll="true"
            :force-auto-scroll-fallback="true"
            :scroll-sensitivity="56"
            :scroll-speed="8"
            class="thumbs-track"
            ghost-class="thumb-ghost"
            @start="onDragStart"
            @end="onDragEnd"
            @change="onThumbsChange"
          >
            <template #item="{ element, index }">
              <div
                class="thumb"
                :class="{ active: element.id === selectedAssetId, checked: isChecked(element.id) }"
                role="button"
                tabindex="0"
                :data-thumb-id="element.id"
                :title="`${index + 1}. ${element.displayName}`"
                @click="selectAsset(element.id)"
                @keydown.enter.prevent="selectAsset(element.id)"
              >
                <label class="thumb-check" @click.stop>
                  <input
                    type="checkbox"
                    :checked="isChecked(element.id)"
                    @change="toggleChecked(element.id)"
                  />
                </label>
                <img
                  :src="assetContentUrl(element.id, previewBust || undefined)"
                  :alt="element.displayName"
                  draggable="false"
                />
              </div>
            </template>
          </draggable>
        </div>
      </section>

      <div class="pane editor">
        <div class="pane-head"><strong>属性</strong></div>
        <template v-if="currentAsset">
          <el-form label-position="top">
            <el-form-item label="显示名称" required>
              <el-input v-model="form.displayName" />
            </el-form-item>
            <el-form-item label="说明">
              <el-input v-model="form.description" type="textarea" :rows="4" />
            </el-form-item>
            <el-form-item label="篇章占位">
              <el-input v-model="form.chapterRefPlaceholder" placeholder="本期仅文本占位" />
            </el-form-item>
            <el-form-item label="标签">
              <el-select
                v-model="form.tagNames"
                multiple
                filterable
                allow-create
                default-first-option
                clearable
                placeholder="输入后回车创建标签"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="关联类型">
              <el-select
                v-model="form.linkType"
                style="width: 100%"
                @change="onFormLinkTypeChange"
              >
                <el-option label="无" value="NONE" />
                <el-option label="系列" value="SERIES" />
                <el-option label="篇章" value="ARC" />
                <el-option label="人物" value="CHARACTER" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="form.linkType === 'SERIES'" label="关联系列">
              <el-select
                v-model="form.seriesIds"
                multiple
                filterable
                clearable
                placeholder="选择系列"
                style="width: 100%"
              >
                <el-option
                  v-for="s in seriesList"
                  :key="s.id"
                  :label="s.name"
                  :value="s.id!"
                />
              </el-select>
            </el-form-item>
            <template v-if="form.linkType === 'ARC'">
              <el-form-item label="所属系列">
                <el-select
                  v-model="seriesForArc"
                  clearable
                  filterable
                  placeholder="先选择系列"
                  style="width: 100%"
                  @change="onFormSeriesForArcChange"
                >
                  <el-option
                    v-for="s in seriesList"
                    :key="s.id"
                    :label="s.name"
                    :value="s.id!"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="关联篇章">
                <el-select
                  v-model="form.arcIds"
                  multiple
                  filterable
                  clearable
                  placeholder="选择篇章"
                  :disabled="typeof seriesForArc !== 'number'"
                  style="width: 100%"
                >
                  <el-option
                    v-for="a in formArcs"
                    :key="a.id"
                    :label="a.title"
                    :value="a.id!"
                  />
                </el-select>
              </el-form-item>
            </template>
            <el-form-item v-if="form.linkType === 'CHARACTER'" label="关联人物">
              <el-select
                v-model="form.characterIds"
                multiple
                filterable
                clearable
                placeholder="选择人物"
                style="width: 100%"
              >
                <el-option
                  v-for="c in characters"
                  :key="c.id"
                  :label="c.name"
                  :value="c.id!"
                />
              </el-select>
            </el-form-item>
          </el-form>
          <ul class="file-info">
            <li>原始文件：{{ currentAsset.originalFilename || '-' }}</li>
            <li>
              尺寸：
              {{ currentAsset.width && currentAsset.height ? `${currentAsset.width} × ${currentAsset.height}` : '-' }}
            </li>
            <li>大小：{{ formatSize(currentAsset.sizeBytes) }}</li>
            <li>类型：{{ currentAsset.contentType || '-' }}</li>
            <li>分类：{{ selectedCategory?.name || '-' }}</li>
          </ul>
          <input
            ref="replaceFileInput"
            type="file"
            accept=".jpg,.jpeg,.png,.webp,.gif,image/jpeg,image/png,image/webp,image/gif"
            hidden
            @change="onReplaceFilePicked"
          />
          <div class="editor-actions">
            <el-button type="primary" :loading="saving" @click="saveMeta">保存</el-button>
            <el-button
              :loading="replacing"
              :disabled="currentAsset.status !== 'NORMAL'"
              @click="triggerReplace"
            >
              替换图片
            </el-button>
            <el-button type="danger" plain :loading="recycling" @click="moveToRecycle">
              移入回收站
            </el-button>
          </div>
        </template>
        <p v-else class="empty">选中素材后可编辑名称与说明</p>
      </div>
    </div>

  </section>

  <el-dialog v-model="batchDialogVisible" title="批量关联" width="480px" destroy-on-close>
    <p class="batch-hint">已选 {{ checkedIds.length }} 项，确认后将覆盖原有关联。</p>
    <el-form label-position="top">
      <el-form-item label="关联类型">
        <el-select
          v-model="batchForm.linkType"
          style="width: 100%"
          @change="onBatchLinkTypeChange"
        >
          <el-option label="无（取消关联）" value="NONE" />
          <el-option label="系列" value="SERIES" />
          <el-option label="篇章" value="ARC" />
          <el-option label="人物" value="CHARACTER" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="batchForm.linkType === 'SERIES'" label="关联系列">
        <el-select
          v-model="batchForm.seriesIds"
          multiple
          filterable
          clearable
          placeholder="选择系列"
          style="width: 100%"
        >
          <el-option
            v-for="s in seriesList"
            :key="s.id"
            :label="s.name"
            :value="s.id!"
          />
        </el-select>
      </el-form-item>
      <template v-if="batchForm.linkType === 'ARC'">
        <el-form-item label="所属系列">
          <el-select
            v-model="batchSeriesForArc"
            clearable
            filterable
            placeholder="先选择系列"
            style="width: 100%"
            @change="onBatchSeriesForArcChange"
          >
            <el-option
              v-for="s in seriesList"
              :key="s.id"
              :label="s.name"
              :value="s.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关联篇章">
          <el-select
            v-model="batchForm.arcIds"
            multiple
            filterable
            clearable
            placeholder="选择篇章"
            :disabled="typeof batchSeriesForArc !== 'number'"
            style="width: 100%"
          >
            <el-option
              v-for="a in batchArcs"
              :key="a.id"
              :label="a.title"
              :value="a.id!"
            />
          </el-select>
        </el-form-item>
      </template>
      <el-form-item v-if="batchForm.linkType === 'CHARACTER'" label="关联人物">
        <el-select
          v-model="batchForm.characterIds"
          multiple
          filterable
          clearable
          placeholder="选择人物"
          style="width: 100%"
        >
          <el-option
            v-for="c in characters"
            :key="c.id"
            :label="c.name"
            :value="c.id!"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="batchDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="batchSaving" @click="confirmBatchLink">确定</el-button>
    </template>
  </el-dialog>

  <ImageLightbox
    v-model="lightboxVisible"
    :src="currentAsset ? assetContentUrl(currentAsset.id, previewBust || undefined) : null"
    :alt="currentAsset?.displayName"
  />
</template>

<style scoped>
.workbench {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 112px);
}
.toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}
.toolbar h2 {
  margin: 8px 0 0;
}
.toolbar-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: flex-end;
}
.editor-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 280px;
  gap: 16px;
  flex: 1;
  min-height: 0;
}
.pane {
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  min-height: 520px;
  box-shadow: 0 10px 30px #24325212;
}
.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.categories ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.category-drop-wrap {
  position: relative;
  min-height: 44px;
  border-radius: 10px;
  overflow: hidden;
}
.category-drop-wrap.active {
  background: #eef3ff;
  color: #3b5bcc;
  font-weight: 600;
}
.category-drop-wrap.drop-ready {
  outline: 2px dashed #5b7cfa;
  outline-offset: -2px;
  background: #f3f6ff;
}
.category-drop-wrap.drop-ready.active {
  outline-color: #9aa8d9;
  background: #eef3ff;
}
.category-drop-zone {
  position: absolute;
  inset: 0;
  z-index: 0;
  min-height: 44px;
  pointer-events: none;
}
.category-drop-wrap.drop-ready .category-drop-zone {
  z-index: 2;
  pointer-events: auto;
}
.cat-row {
  position: relative;
  z-index: 1;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: inherit;
}
.category-drop-wrap.drop-ready .cat-row {
  pointer-events: none;
}
.drop-hint {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: #3b5bcc;
}
.drop-chip {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}
.cat-name {
  color: #172033;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.category-drop-wrap.active .cat-name {
  color: #3b5bcc;
}
.drag-tip {
  margin: 0;
  font-size: 12px;
  color: #6f7e9d;
}
.preview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.preview-main {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-height: 0;
}
.preview-img-btn {
  padding: 0;
  border: 0;
  background: transparent;
  cursor: zoom-in;
  display: inline-flex;
  max-width: 100%;
  border-radius: 12px;
}
.preview-img-btn:focus-visible {
  outline: 2px solid #2f6fed;
  outline-offset: 2px;
}
.preview-main img {
  max-width: 100%;
  max-height: 360px;
  object-fit: contain;
  background: #f4f6fa;
  border-radius: 12px;
  display: block;
}
.preview-meta {
  display: flex;
  gap: 12px;
  align-items: baseline;
  color: #6f7e9d;
}
.nav {
  display: flex;
  gap: 8px;
  align-items: center;
}
.empty-preview,
.empty {
  color: #6f7e9d;
  padding: 24px 8px;
}
.search-reorder-hint {
  margin: 0;
  font-size: 12px;
  color: #9aa8d9;
  line-height: 1.5;
}
.thumbs {
  overflow-x: auto;
  padding-bottom: 6px;
  min-height: 80px;
}
.thumbs-track {
  display: flex;
  gap: 8px;
  width: max-content;
  min-width: 100%;
  min-height: 72px;
}
.thumb {
  position: relative;
  flex: 0 0 auto;
  width: 72px;
  height: 72px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: 10px;
  overflow: hidden;
  cursor: grab;
  background: #f4f6fa;
}
.thumb-check {
  position: absolute;
  top: 4px;
  left: 4px;
  z-index: 2;
  display: flex;
  margin: 0;
  padding: 2px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.85);
  cursor: pointer;
}
.thumb-check input {
  margin: 0;
  cursor: pointer;
}
.thumb-ghost {
  opacity: 0.4;
}
.thumb.active {
  border-color: #3b5bcc;
}
.thumb.checked {
  box-shadow: inset 0 0 0 2px #67c23a;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.batch-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: #4a5878;
}
.file-info {
  list-style: none;
  padding: 0;
  margin: 0 0 16px;
  color: #6f7e9d;
  font-size: 13px;
  line-height: 1.8;
}
@media (max-width: 1100px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
