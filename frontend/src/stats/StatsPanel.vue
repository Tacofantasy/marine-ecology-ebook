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
    title: { text: '近 30 天趋势', left: 16, textStyle: { color: '#12384a', fontSize: 14, fontWeight: 700 } },
    tooltip: { trigger: 'axis' },
    legend: { data: ['日增阅读量', '日增点赞量'], top: 2, right: 58 },
    grid: { left: 48, right: 24, top: 58, bottom: 34 },
    toolbox: {
      feature: {
        saveAsImage: { title: '导出图片', name: 'marine-ebook-trend-30d' },
      },
      right: 16,
    },
    xAxis: {
      type: 'category',
      data: trend.value.map((point) => point.date.slice(5)),
      axisLabel: { interval: 4, color: '#68828c' },
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
        itemStyle: { color: '#08658a' },
        lineStyle: { width: 3 },
        areaStyle: { opacity: 0.06 },
      },
      {
        name: '日增点赞量',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: trend.value.map((point) => point.likeDelta),
        itemStyle: { color: '#16a8b8' },
        lineStyle: { width: 3, type: 'dashed' },
        areaStyle: { opacity: 0.05 },
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
        <p class="stats-a11y-summary">平台当前共有 {{ formatNumber(summary?.publishedEbookCount) }} 本已发布电子书，累计阅读 {{ formatNumber(summary?.totalViewCount) }} 次，累计点赞 {{ formatNumber(summary?.totalLikeCount) }} 次。</p>
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
  margin: 20px 0 0;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.stats-card {
  min-width: 0;
  padding: 17px;
  background: #fff;
  border: 1px solid #d5e5e7;
  border-radius: 14px;
  box-shadow: 0 8px 24px rgba(7, 56, 78, 0.05);
}

.stats-label {
  margin: 0 0 9px;
  overflow: hidden;
  color: #527180;
  font-size: 0.76rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stats-value {
  margin: 0;
  overflow: hidden;
  color: #08658a;
  font-size: clamp(1.2rem, 2vw, 1.65rem);
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.stats-value-text {
  font-size: 1.05rem;
}

.stats-chart-card {
  margin-top: 12px;
  padding: 18px 12px 8px;
  background: #fff;
  border: 1px solid #d5e5e7;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(7, 56, 78, 0.05);
}

.stats-chart {
  width: 100%;
  height: 320px;
}

.stats-error {
  padding: 24px;
  color: #a33333;
  text-align: center;
  background: #fff5f5;
  border: 1px solid #fecaca;
  border-radius: 14px;
}

.stats-a11y-summary {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 1080px) {
  .stats-cards {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .stats-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stats-chart-card {
    padding-inline: 0;
  }

  .stats-chart {
    height: 280px;
  }
}
</style>
