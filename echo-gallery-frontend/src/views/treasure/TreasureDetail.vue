<script setup lang="ts">
import SidePanel from '../../components/ui/SidePanel.vue'
import TreasureWorkLink from '../../components/work/TreasureWorkLink.vue'
import CoverImage from '../../components/ui/CoverImage.vue'
import { computed, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Edit, MoreFilled, View } from '@element-plus/icons-vue'
import ExpandableText from '../../components/ExpandableText.vue'
import TreasureRankBadge from '../../components/treasure/TreasureRankBadge.vue'
import TreasureFormDialog from '../../components/treasure/TreasureFormDialog.vue'
import TreasureTrialDialog from '../../components/treasure/TreasureTrialDialog.vue'
import TreasureYieldHistory from '../../components/treasure/TreasureYieldHistory.vue'
import TreasureChallengeRoute from '../../components/treasure/TreasureChallengeRoute.vue'
import { treasureApi } from '../../utils/api/treasureApi'
import { nextRankOf, errorMessage } from '../../utils/treasure'
import type { TreasureRank } from '../../types/treasure'
import { formatDate } from '../../utils/formatDate'
import DetailShell from '../../components/ui/DetailShell.vue'
import DetailHeading from '../../components/ui/DetailHeading.vue'

const route = useRoute()
const router = useRouter()
const id = computed(() => Number(route.params.id))
const validId = computed(() => Number.isSafeInteger(id.value) && id.value > 0)
const queryClient = useQueryClient()
const detail = useQuery({ queryKey: computed(() => ['treasures', 'detail', id.value]), queryFn: () => treasureApi.detail(id.value), enabled: validId })
const treasure = computed(() => detail.data.value)
const relationPage = ref(0)
watch(id, () => { relationPage.value = 0; editVisible.value = false; trialVisible.value = false; selectedRank.value = 'BRONZE'; dialogRank.value = 'BRONZE' })
const routeQuery = useQuery({ queryKey: computed(() => ['treasures', id.value, 'route']), queryFn: () => treasureApi.route(id.value), enabled: computed(() => !!treasure.value) })
const derived = useQuery({ queryKey: computed(() => ['treasures', id.value, 'relations', relationPage.value]), queryFn: () => treasureApi.relations(id.value, relationPage.value), enabled: computed(() => !!treasure.value) })
const editVisible = ref(false)
const trialVisible = ref(false)
const selectedRank = ref<TreasureRank>('BRONZE')
const dialogRank = ref<TreasureRank>('BRONZE')
const initialMode = ref<'route' | 'help' | 'edit' | 'attempt'>('route')
const busy = ref(false)
watch(() => treasure.value?.id, () => {
  if (treasure.value) selectedRank.value = treasure.value.activeTrial?.targetRank ?? nextRankOf(treasure.value.rank) ?? 'GOLD'
})
const openChallenge = (mode: 'route' | 'help' | 'edit' | 'attempt', rank?: TreasureRank) => {
  dialogRank.value = rank ?? dialogRank.value
  initialMode.value = mode; trialVisible.value = true
}
const hasSources = computed(() => !!treasure.value && Boolean(treasure.value.sourceCard || treasure.value.sourceExperiment || treasure.value.sourceTreasures.length))
const hasLineage = computed(() => hasSources.value || derived.isLoading.value || derived.isError.value || Boolean(derived.data.value?.content.length))
const refresh = () => Promise.all([queryClient.invalidateQueries({ queryKey: ['treasures'] }), queryClient.invalidateQueries({ queryKey: ['overview'] }), queryClient.invalidateQueries({ queryKey: ['treasure-source-picker'] })])
const archive = async () => {
  if (busy.value || !treasure.value) return
  busy.value = true
  try { await treasureApi.archive(id.value, !treasure.value.isArchived); await refresh() } catch (e) { ElMessage.error(errorMessage(e)) } finally { busy.value = false }
}
const remove = async () => {
  if (busy.value) return
  try { await ElMessageBox.confirm('永久刪除此奇珍及其試煉、收益紀錄和來源關聯？刪除後無法復原；其他成果與原始卡片不會刪除。', '刪除奇珍', { confirmButtonText: '永久刪除', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  busy.value = true
  try { await treasureApi.delete(id.value); await router.push('/treasures'); await refresh() } catch (e) { ElMessage.error(errorMessage(e)) } finally { busy.value = false }
}
const handleCommand = (command: string) => {
  if (command === 'archive') archive()
  else if (command === 'delete') remove()
}
</script>

<template>
  <DetailShell class="treasure-detail">
    <template #navigation>
    <header class="detail-navigation app-detail-navigation">
      <el-button text :icon="ArrowLeft" @click="router.push('/treasures')">返回奇珍展覽館</el-button>
      <div v-if="treasure" class="detail-actions">
        <el-button type="primary" plain :icon="Edit" :disabled="busy" @click="editVisible = true">編輯奇珍</el-button>
        <el-dropdown trigger="click" @command="handleCommand">
          <el-button :icon="MoreFilled" :disabled="busy">更多操作</el-button>
          <template #dropdown><el-dropdown-menu><el-dropdown-item command="archive">{{ treasure.isArchived ? '還原奇珍' : '封存奇珍' }}</el-dropdown-item><el-dropdown-item command="delete" divided>永久刪除</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
      </div>
    </header>
    </template>
    <p v-if="detail.isLoading.value">正在讀取奇珍…</p>
    <el-result v-else-if="detail.isError.value || !validId" icon="error" title="無法讀取這件奇珍"><template #extra><el-button @click="detail.refetch()">重試</el-button></template></el-result>
    <template v-else-if="treasure">
      <main class="treasure-workspace">
      <DetailHeading eyebrow="已完成的成果" :title="treasure.title">
        <template #metadata>
          <TreasureRankBadge :rank="treasure.rank" />
          <span>完成於 {{ treasure.acquiredOn }}</span><el-tag v-if="treasure.isArchived" type="info">已封存</el-tag>
        </template>
      </DetailHeading>
      <div class="detail-grid">
        <div class="detail-main-column">
        <section class="detail-panel detail-content">
          <h2>成果內容</h2>
          <TreasureWorkLink :treasure-id="id" />
          <CoverImage :src="treasure.coverImageUrl" alt="成果封面" class="artifact-cover" />
          <div v-if="treasure.description" class="reading-copy"><ExpandableText :content="treasure.description" :lines="7" /></div>
          <p v-else class="empty-copy">這件奇珍的來歷尚未寫下。可透過「編輯奇珍」補充內容與經驗。</p>
          <a v-if="treasure.artifactUrl" class="artifact-link" :href="treasure.artifactUrl" target="_blank" rel="noopener noreferrer"><el-icon><View /></el-icon>開啟作品</a>
          <p class="metadata">入藏於 {{ formatDate(treasure.createdAt) }} · 最近更新 {{ formatDate(treasure.updatedAt) }}</p>
          <section v-if="hasLineage" class="lineage-context" aria-labelledby="lineage-title">
            <div class="lineage-heading"><h3 id="lineage-title">成果脈絡</h3><span class="metadata">{{ treasure.sourceTreasures.length + (treasure.sourceCard ? 1 : 0) + (treasure.sourceExperiment ? 1 : 0) }} 個來源<span v-if="derived.data.value"> · {{ derived.data.value.totalElements }} 件衍生成果</span></span></div>
            <div v-if="hasSources" class="lineage-branch">
              <span class="lineage-label">來自</span>
              <div class="lineage-links">
                <router-link v-if="treasure.sourceCard" :to="`/card/${treasure.sourceCard.id}`">卡片 · {{ treasure.sourceCard.title }}</router-link>
                <router-link v-if="treasure.sourceExperiment" :to="`/experiments/${treasure.sourceExperiment.id}`">實驗 · {{ treasure.sourceExperiment.title }}</router-link>
                <router-link v-for="source in treasure.sourceTreasures" :key="source.id" :to="`/treasures/${source.id}`">奇珍 · {{ source.title }}</router-link>
              </div>
            </div>
            <div v-if="derived.isLoading.value" class="lineage-branch"><span class="lineage-label">延伸為</span><span class="metadata">正在讀取關聯…</span></div>
            <div v-else-if="derived.isError.value" class="lineage-branch"><span class="lineage-label">延伸為</span><el-button text @click="derived.refetch()">關聯讀取失敗，重試</el-button></div>
            <div v-else-if="derived.data.value?.content.length" class="lineage-branch">
              <span class="lineage-label">延伸為</span>
              <div class="lineage-links"><router-link v-for="result in derived.data.value.content" :key="result.id" :to="`/treasures/${result.id}`">{{ result.title }}</router-link></div>
            </div>
            <el-pagination v-if="(derived.data.value?.totalElements ?? 0) > 10" :current-page="relationPage + 1" :page-size="10" :total="derived.data.value?.totalElements ?? 0" layout="prev, pager, next" @current-change="relationPage = $event - 1" />
          </section>
        </section>
        <section class="detail-panel yield-panel"><TreasureYieldHistory :treasure-id="id" :archived="treasure.isArchived" compact /></section>
        </div>
        <SidePanel class="challenge-panel">
          <p v-if="routeQuery.isLoading.value">正在讀取試煉…</p>
          <el-button v-else-if="routeQuery.isError.value" @click="routeQuery.refetch()">讀取失敗，重試</el-button>
          <TreasureChallengeRoute v-else v-model="selectedRank" :treasure-id="id" :rank="treasure.rank" :trials="routeQuery.data.value ?? []" :archived="treasure.isArchived" @edit="openChallenge('edit', $event)" @attempt="openChallenge('attempt', $event.targetRank ?? undefined)" @help="openChallenge('help')" />
        </SidePanel>
      </div>
      </main>
      <TreasureFormDialog v-model="editVisible" :treasure="treasure" />
      <TreasureTrialDialog v-if="trialVisible" v-model="trialVisible" :treasure-id="id" :current-rank="treasure.rank" :next-rank="treasure.nextRank" :archived="treasure.isArchived" :initial-rank="dialogRank" :initial-mode="initialMode" />
    </template>
  </DetailShell>
</template>

<style scoped>
.treasure-workspace { display: flex; flex-direction: column; gap: var(--space-lg); }
/* 閱讀為主、挑戰為輔；900px 以下重排，維持內容可讀。 */
.detail-grid { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(0, 1fr); gap: var(--space-lg); align-items: start; }
.detail-main-column { display: flex; flex-direction: column; gap: var(--space-lg); min-width: 0; }
.detail-panel { min-width: 0; padding: var(--panel-padding); border: 1px solid var(--el-border-color-light); background: var(--el-bg-color); border-radius: var(--radius-md); }
.detail-panel h2 { margin: 0; font-size: var(--type-section-title); overflow-wrap: anywhere; }
.detail-panel h3 { margin: 0; font-size: var(--type-card-title); overflow-wrap: anywhere; }
.reading-copy { overflow-wrap: anywhere; margin: var(--space-md) 0; max-width: var(--reading-max-width); }
.empty-copy { margin-top: var(--space-md); color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-body); }
.artifact-link { display: inline-flex; gap: var(--space-xs); align-items: center; color: var(--el-color-primary); text-decoration: none; }
.metadata { color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-metadata); }
.artifact-cover { width: 100%; margin-top: var(--space-md); border-radius: var(--radius-sm); }
.lineage-context { margin-top: var(--space-lg); padding-top: var(--space-md); border-top: 1px solid var(--el-border-color-light); }
.lineage-heading { display: flex; justify-content: space-between; align-items: baseline; gap: var(--space-sm); }
.lineage-heading h3 { color: var(--el-text-color-regular); font-size: var(--type-ui); font-weight: var(--weight-semibold); }
.lineage-branch { display: grid; grid-template-columns: 64px minmax(0, 1fr); gap: var(--space-sm); align-items: start; margin-top: var(--space-sm); }
.lineage-label { color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.lineage-links { display: flex; flex-wrap: wrap; gap: var(--space-xs); }
.lineage-links a { display: inline-flex; max-width: 100%; padding: var(--space-2xs) var(--space-xs); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-sm); color: var(--el-color-primary); font-size: var(--type-meta); line-height: var(--leading-metadata); text-decoration: none; overflow-wrap: anywhere; }
.lineage-links a:hover { border-color: var(--el-color-primary-light-5); background: var(--surface-subtle); }
.yield-panel { width: 100%; box-sizing: border-box; }
.challenge-panel { box-shadow: none; }
@media (max-width: 900px) {
  .detail-grid { grid-template-columns: minmax(0, 1fr); }
  .challenge-panel { grid-row: 1; }
  .detail-main-column { grid-row: 2; }
}
@media (max-width: 760px) {
  .lineage-heading { align-items: flex-start; flex-direction: column; gap: var(--space-2xs); }
}
</style>
