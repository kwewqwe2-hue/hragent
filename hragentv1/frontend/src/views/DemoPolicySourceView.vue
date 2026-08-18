<template>
  <div class="policy-source-page">
    <header class="source-header">
      <div class="source-brand">
        <span class="brand-icon"><Document /></span>
        <div>
          <strong>{{ policy?.sourceName || '示例市人力资源政策演示中心' }}</strong>
          <span>政策信息公开</span>
        </div>
      </div>
      <el-tag type="danger" effect="plain">演示数据源</el-tag>
    </header>

    <main class="policy-main" v-loading="loading">
      <el-alert
        v-if="policy"
        :title="policy.disclaimer"
        type="warning"
        :closable="false"
        show-icon
      />

      <el-result
        v-if="loadError"
        icon="error"
        title="政策页面加载失败"
        :sub-title="loadError"
      >
        <template #extra>
          <el-button type="primary" @click="load">重新加载</el-button>
        </template>
      </el-result>

      <article v-else-if="policy" class="policy-article">
        <div class="article-eyebrow">政策公开 · {{ policy.region }}</div>
        <h1>{{ policy.title }}</h1>
        <div class="article-meta">
          <span>版本 <code>{{ policy.version }}</code></span>
          <span>发布日期 {{ policy.publishedAt }}</span>
          <span>生效日期 {{ policy.effectiveAt }}</span>
        </div>

        <p class="article-summary">{{ policy.summary }}</p>

        <div class="article-content">
          <p v-for="paragraph in paragraphs" :key="paragraph">{{ paragraph }}</p>
        </div>

        <section class="change-note">
          <strong>本版变更</strong>
          <p>{{ policy.changeSummary }}</p>
        </section>

        <footer class="article-footer">
          <span>来源：{{ policy.sourceName }}</span>
          <span>内容校验：{{ policy.contentHash.slice(0, 12) }}</span>
        </footer>
      </article>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Document } from '@element-plus/icons-vue'
import { getData } from '../api/http'
import type { DemoPolicy } from '../api/types'

const policy = ref<DemoPolicy | null>(null)
const loading = ref(false)
const loadError = ref('')
const paragraphs = computed(() => policy.value?.content.split('\n').filter(Boolean) || [])

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    policy.value = await getData<DemoPolicy>('/demo-policy/current')
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '无法连接演示政策数据源'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.policy-source-page {
  min-height: 100vh;
  color: #252b2a;
  background: #f4f6f5;
}

.source-header {
  min-height: 76px;
  padding: 0 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  color: #ffffff;
  background: #174f49;
  border-bottom: 4px solid #caa44c;
}

.source-brand {
  display: flex;
  align-items: center;
  gap: 13px;
}

.source-brand strong,
.source-brand span {
  display: block;
}

.source-brand strong {
  font-size: 18px;
  line-height: 1.4;
}

.source-brand > div > span {
  margin-top: 2px;
  color: #d8e7e4;
  font-size: 12px;
}

.brand-icon {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  color: #174f49;
  background: #ffffff;
  border-radius: 4px;
}

.brand-icon :deep(svg) {
  width: 22px;
  height: 22px;
}

.policy-main {
  width: min(960px, calc(100% - 48px));
  min-height: calc(100vh - 76px);
  margin: 0 auto;
  padding: 28px 0 64px;
}

.policy-article {
  margin-top: 22px;
  padding: 34px 48px 38px;
  background: #ffffff;
  border: 1px solid #dce2df;
  border-top: 3px solid #174f49;
}

.article-eyebrow {
  color: #65716e;
  font-size: 13px;
}

.policy-article h1 {
  margin: 12px 0 16px;
  color: #1f2927;
  font-size: 26px;
  line-height: 1.45;
  text-align: center;
  letter-spacing: 0;
}

.article-meta {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px 24px;
  padding-bottom: 20px;
  color: #64706d;
  border-bottom: 1px solid #e3e8e6;
  font-size: 13px;
}

.article-meta code {
  padding: 2px 7px;
  color: #155e59;
  background: #e9f4f1;
  border-radius: 999px;
  font-family: inherit;
  font-weight: 700;
}

.article-summary {
  margin: 24px 0;
  padding: 13px 16px;
  color: #35423f;
  background: #f3f6f5;
  border-left: 3px solid #6b8f88;
  line-height: 1.75;
}

.article-content p {
  margin: 0 0 16px;
  color: #2e3634;
  font-size: 15px;
  line-height: 1.95;
}

.change-note {
  margin-top: 28px;
  padding: 14px 16px;
  background: #fff9eb;
  border: 1px solid #ead9ac;
}

.change-note strong {
  color: #755b1c;
  font-size: 14px;
}

.change-note p {
  margin: 6px 0 0;
  color: #5e5540;
  line-height: 1.7;
}

.article-footer {
  margin-top: 28px;
  padding-top: 16px;
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 20px;
  color: #73807d;
  border-top: 1px solid #e3e8e6;
  font-size: 12px;
}

@media (max-width: 720px) {
  .source-header {
    padding: 0 20px;
  }

  .policy-main {
    width: min(100% - 24px, 960px);
  }

  .policy-article {
    padding: 26px 20px 30px;
  }

  .policy-article h1 {
    font-size: 21px;
  }
}
</style>
