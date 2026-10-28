<template>
  <section class="detail-shell app-page">
    <div class="detail-shell-navigation"><slot name="navigation" /></div>
    <div class="detail-shell-content"><slot /></div>
  </section>
</template>
<style scoped>
.detail-shell { min-height: 100%; background: var(--surface-page); }
.detail-shell-navigation { background: var(--el-bg-color); border-bottom: 1px solid var(--el-border-color-light); }
.detail-shell-navigation :deep(.app-detail-navigation) { min-height: 48px; padding: var(--space-sm) var(--space-md); border-bottom: 0; }
.detail-shell-content { width: min(100%, var(--content-max-width)); margin-inline: auto; padding: var(--page-gutter); box-sizing: border-box; }
.detail-shell-navigation :deep(.detail-actions) { display: flex; align-items: center; gap: var(--space-xs); }
@media (max-width: 760px) {
  .detail-shell-navigation :deep(.app-detail-navigation) { flex-direction: column; align-items: stretch; }
  .detail-shell-navigation :deep(.app-detail-navigation > .el-button:first-child) { align-self: flex-start; }
  .detail-shell-navigation :deep(.detail-actions) { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-xs); }
  .detail-shell-navigation :deep(.detail-actions .el-button) { width: 100%; min-width: 0; margin-left: 0; }
}
</style>
