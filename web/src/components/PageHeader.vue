<template>
  <div class="page-header">
    <div class="titles">
      <h2>{{ title }}</h2>
      <p v-if="desc" class="desc">{{ desc }}</p>
    </div>
    <div class="actions">
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  desc: { type: String, default: '' },
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}
.titles h2 {
  margin: 0;
  font-size: 20px;
  line-height: 28px;
  color: var(--qz-text);
}
.desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--qz-text-muted);
  line-height: 1.5;
}
.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
}
</style>
