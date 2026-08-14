<script setup lang="ts">
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useUiStore } from '../stores/ui'
import type { PricePoint } from '../types'
import { formatMoney } from '../utils/format'

echarts.use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{
  title: string
  subtitle: string
  points: PricePoint[]
  loading?: boolean
  valueType?: 'price' | 'index'
}>()

const el = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let observer: ResizeObserver | null = null
const ui = useUiStore()

function cssVar(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

function valueText(value: number) {
  return props.valueType === 'index' ? value.toFixed(2) : formatMoney(value)
}

function render() {
  if (!el.value || props.loading || props.points.length === 0) return
  if (!chart) {
    chart = echarts.init(el.value)
    observer = new ResizeObserver(() => chart?.resize())
    observer.observe(el.value)
  }
  const first = props.points[0]?.value ?? 0
  const last = props.points[props.points.length - 1]?.value ?? first
  const up = cssVar('--success', '#0b9a52')
  const down = cssVar('--danger', '#d33c4b')
  const muted = cssVar('--text-muted', '#8a91a0')
  const border = cssVar('--border', '#eef0f3')
  const surface = cssVar('--surface-solid', '#17181c')
  const text = cssVar('--text', '#fff')
  const color = last >= first ? up : down
  chart.setOption({
    animationDuration: 180,
    tooltip: {
      trigger: 'axis',
      backgroundColor: surface,
      borderWidth: 0,
      textStyle: { color: text, fontSize: 12 },
      valueFormatter: (value: unknown) => valueText(Number(value))
    },
    grid: { left: 12, right: 18, top: 20, bottom: 8, containLabel: true },
    xAxis: {
      type: 'time',
      boundaryGap: false,
      axisLine: { lineStyle: { color: border } },
      axisTick: { show: false },
      axisLabel: { color: muted, hideOverlap: true }
    },
    yAxis: {
      type: 'value',
      scale: true,
      splitNumber: 4,
      splitLine: { lineStyle: { color: border } },
      axisLabel: { color: muted, formatter: (value: number) => valueText(value) }
    },
    series: [{
      name: props.valueType === 'index' ? '指数' : 'UU 价',
      type: 'line',
      showSymbol: false,
      smooth: false,
      lineStyle: { width: 2, color },
      itemStyle: { color },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: `${color}28` },
        { offset: 1, color: `${color}02` }
      ]) },
      data: props.points.map(point => [point.at, point.value])
    }]
  }, true)
}

async function sync() {
  await nextTick()
  if (props.points.length === 0 || props.loading) {
    chart?.dispose()
    chart = null
    observer?.disconnect()
    observer = null
    return
  }
  render()
}

onMounted(sync)
watch(() => [props.points, props.loading, props.title], sync, { deep: true, flush: 'post' })
watch(() => ui.theme, sync)
onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <section class="card market-chart">
    <header>
      <div>
        <h2>{{ title }}</h2>
        <p>{{ subtitle }}</p>
      </div>
    </header>
    <div v-if="loading" class="chart-state" aria-live="polite">
      <div class="skeleton chart-skeleton"></div>
    </div>
    <div v-else-if="points.length === 0" class="chart-state empty-state">
      <p>暂无足够的行情快照。刷新两次行情后即可形成趋势线。</p>
    </div>
    <div v-else ref="el" class="chart-canvas" role="img" :aria-label="`${title}趋势图`"></div>
  </section>
</template>

<style scoped>
.market-chart { overflow: hidden; }
header { padding: 16px 18px 0; }
h2 { margin: 0; font-size: 15px; }
p { margin: 3px 0 0; color: var(--text-muted); font-size: 12px; }
.chart-canvas, .chart-state { height: 300px; padding: 8px 10px 12px; }
.chart-skeleton { width: 100%; height: 100%; }
.chart-state.empty-state { height: 220px; justify-content: center; }
@media (max-width: 640px) { .chart-canvas, .chart-state { height: 240px; } }
</style>
