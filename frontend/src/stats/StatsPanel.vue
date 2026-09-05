<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { getStatsSummary, getStatsTrend, type StatsSummary, type TrendPoint } from '../stats/stats-api'

const summary = ref<StatsSummary | null>(null)
const trend = ref<TrendPoint[]>([])
const loading = ref(false)
const errorText = ref('')
const chartContainer = ref<HTMLDivElement | null>(null)

let chart: import('echarts/core').ECharts | null = null
let disposed = false
let resizeObserver: ResizeObserver | null = null

function formatNumber(value: number | undefined): string {
  if (value === undefined || value === null) return '—'
  return value.toLocaleString('zh-CN')
}

function formatReadingTime(minutes: number | undefined): string {
  if (!minutes) return '—'
  if (minutes < 60) return `约 ${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest > 0 ? `约 ${hours} 小时 ${rest} 分钟` : `约 ${hours} 小时`
}

async function loadStats() {
  if (loading.value) return
  loading.value = true
  errorText.value = ''
  try {
    const [summaryData, trendData, { init }] = await Promise.all([
      getStatsSummary(), getStatsTrend(30), import('./chart'),
    ])
    if (disposed) return
    summary.value = summaryData
    trend.value = trendData
    await nextTick()
    if (disposed || !chartContainer.value) return
    if (!chart) {
      chart = init(chartContainer.value)
      resizeObserver = new ResizeObserver(() => chart?.resize())
      resizeObserver.observe(chartContainer.value)
    }
    renderChart()
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '统计数据加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function renderChart() {
  if (!chartContainer.value || !chart) return
  chart.setOption({
    title: { text: '近 30 天阅读 / 点赞趋势', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    legend: { data: ['日增阅读量', '日增点赞量'], top: 28 },
    grid: { left: 48, right: 24, top: 64, bottom: 32 },
    toolbox: {
      feature: {
        saveAsImage: { title: '导出图片', name: 'marine-ebook-trend-30d' },
      },
      right: 16,
    },
    xAxis: {
      type: 'category',
      data: trend.value.map((point) => point.date.slice(5)),
      axisLabel: { rotate: 45 },
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '日增阅读量',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: trend.value.map((point) => point.viewDelta),
        itemStyle: { color: '#075985' },
        areaStyle: { opacity: 0.08 },
      },
      {
        name: '日增点赞量',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: trend.value.map((point) => point.likeDelta),
        itemStyle: { color: '#0e7490' },
        areaStyle: { opacity: 0.08 },
      },
    ],
  })
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  void loadStats()
})

onBeforeUnmount(() => {
  disposed = true
  resizeObserver?.disconnect()
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <section class="stats-section" aria-label="平台统计">
    <a-spin :spinning="loading">
      <div v-if="errorText" class="stats-error">
        <p>{{ errorText }}</p>
        <a-button size="small" :loading="loading" @click="loadStats">重新加载</a-button>
      </div>

      <div v-show="!errorText">
        <div class="stats-cards">
          <div class="stats-card">
            <p class="stats-label" title="当前在库电子书的累计阅读；删除电子书后不再计入">在库累计阅读</p>
            <p class="stats-value">{{ formatNumber(summary?.totalViewCount) }}</p>
          </div>
          <div class="stats-card">
            <p class="stats-label" title="当前保留的点赞记录数；取消点赞或删除电子书后减少">在库累计点赞</p>
            <p class="stats-value">{{ formatNumber(summary?.totalLikeCount) }}</p>
          </div>
          <div class="stats-card">
            <p class="stats-label">今日阅读</p>
            <p class="stats-value">{{ formatNumber(summary?.todayViewCount) }}</p>
          </div>
          <div class="stats-card">
            <p class="stats-label">今日点赞</p>
            <p class="stats-value">{{ formatNumber(summary?.todayLikeCount) }}</p>
          </div>
          <div class="stats-card">
            <p class="stats-label">已发布电子书</p>
            <p class="stats-value">{{ formatNumber(summary?.publishedEbookCount) }}</p>
          </div>
          <div class="stats-card">
            <p class="stats-label">预计阅读时长</p>
            <p class="stats-value stats-value-text">{{ formatReadingTime(summary?.estimatedReadingMinutes) }}</p>
          </div>
        </div>

        <div class="stats-chart-card">
          <div ref="chartContainer" class="stats-chart" role="img" aria-label="近 30 天阅读与点赞趋势图"></div>
        </div>
      </div>
    </a-spin>
  </section>
</template>

<style scoped>
.stats-section {
  margin: 32px 0;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 16px;
}

.stats-card {
  background: #fff;
  border: 1px solid #d9edf2;
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(7, 89, 133, 0.06);
}

.stats-label {
  margin: 0 0 6px;
  font-size: 0.85rem;
  color: #48677a;
}

.stats-value {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
  color: #075985;
  font-variant-numeric: tabular-nums;
}

.stats-value-text {
  font-size: 1.15rem;
}

.stats-chart-card {
  margin-top: 16px;
  background: #fff;
  border: 1px solid #d9edf2;
  border-radius: 12px;
  padding: 16px 8px 8px;
}

.stats-chart {
  width: 100%;
  height: 320px;
}

.stats-error {
  text-align: center;
  padding: 24px;
  color: #a04040;
}
</style>
