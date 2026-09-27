<script setup lang="ts">
// Base application shell. Establishes the desktop-first layout regions
// (header / main content / sidebar) that feature components will fill in
// later tasks. Dark mode is applied via the [data-theme="dark"] attribute
// set on the document root in index.html.
const appName = 'MeetHalfway'
</script>

<template>
  <div class="app-container">
    <header class="app-header">
      <h1 class="app-title">{{ appName }}</h1>
    </header>

    <main class="app-main">
      <section class="app-content" aria-label="content">
        <!-- Feature views mount here (StrategyComparisonView, etc.) -->
      </section>
      <aside class="app-sidebar" aria-label="sidebar">
        <!-- Controls mount here (AddressSearchBox, LanguageSelector, etc.) -->
      </aside>
    </main>
  </div>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: var(--space-3);
  margin-bottom: var(--space-4);
  border-bottom: 1px solid var(--color-border);
}

.app-title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
}

.app-content,
.app-sidebar {
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-4);
  min-height: 240px;
}
</style>
