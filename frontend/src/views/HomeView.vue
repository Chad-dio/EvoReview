<template>
  <main class="home">
    <h1>EvoReview</h1>
    <p>Self-Evolving Git Code Review Agent</p>
    <p>Frontend is running.</p>
    <ul class="status-list">
      <li>Backend: {{ backendStatus }}</li>
      <li>LLM Service: {{ llmStatus }}</li>
    </ul>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'

const backendStatus = ref('checking')
const llmStatus = ref('checking')

onMounted(async () => {
  try {
    const response = await fetch('/api/stack')
    if (!response.ok) {
      backendStatus.value = 'DOWN'
      llmStatus.value = 'UNKNOWN'
      return
    }
    const data = await response.json() as {
      backend?: { status?: string }
      llmService?: { status?: string }
    }
    backendStatus.value = data.backend?.status ?? 'DOWN'
    llmStatus.value = data.llmService?.status ?? 'DOWN'
  } catch {
    backendStatus.value = 'DOWN'
    llmStatus.value = 'UNKNOWN'
  }
})
</script>

<style scoped>
.home {
  font-family: system-ui, sans-serif;
  text-align: center;
  padding: 4rem 1rem;
  color: #1f2937;
}

h1 {
  margin: 0 0 0.75rem;
  font-size: 2.5rem;
}

p {
  margin: 0.35rem 0;
  font-size: 1.1rem;
}

.status-list {
  list-style: none;
  margin: 1.5rem 0 0;
  padding: 0;
  font-size: 1rem;
}

.status-list li {
  margin: 0.25rem 0;
}
</style>
