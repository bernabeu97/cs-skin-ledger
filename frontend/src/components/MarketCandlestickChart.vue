<script setup lang="ts">
import * as echarts from 'echarts/core'
import { CandlestickChart } from 'echarts/charts'
import { DataZoomComponent, GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useUiStore } from '../stores/ui'
import type { CsqaqCandle } from '../types'

echarts.use([CandlestickChart, DataZoomComponent, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{
  title: string
  subtitle: string
  points: CsqaqCandle[]
  loading?: boolean
}>()

const el = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let observer: ResizeObserver | null = null
const ui = useUiStore()

function cssVar(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

function render() {
  if (!el.value || props.loading || props.points.length === 0) return
  if (!chart) {
    chart = echarts.init(el.value)
    observer = new ResizeObserver(() => chart?.resize())
    observer.observe(el.value)
  }
  const visibleCount = Math.min(120, props.points.length)
  const start = Math.max(0, 100 - visibleCount / props.points.length * 100)
  const up = cssVar('--success', '#0b9a52')
  const down = cssVar('--danger', '#d33c4b')
  const muted = cssVar('--text-muted', '#8a91a0')
  const border = cssVar('--border', '#eef0f3')
  const surface = cssVar('--surface-solid', '#17181c')
  const text = cssVar('--text', '#fff')
  const accentSoft = cssVar('--accent-soft', 'rgba(45,212,191,.14)')
  chart.setOption({
    animationDuration: 160,
    tooltip: {
      trigger: 'axis',
      backgroundColor: surface,
      borderWidth: 0,
      textStyle: { color: text, fontSize: 11 }
    },
    grid: { left: 12, right: 18, top: 16, bottom: 42, containLabel: true },
    xAxis: {
      type: 'category',
      data: props.points.map(point => new Date(point.at).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit' })),
      boundaryGap: true,
      axisLine: { lineStyle: { color: border } },
      axisTick: { show: false },
      axisLabel: { color: muted, hideOverlap: true }
    },
    yAxis: {
      scale: true,
      splitNumber: 4,
      splitLine: { lineStyle: { color: border } },
      axisLabel: { color: muted, formatter: (value: number) => value.toFixed(0) }
    },
    dataZoom: [
      { type: 'inside', start, end: 100 },
      { type: 'slider', start, end: 100, height: 14, bottom: 7, borderColor: 'transparent', fillerColor: accentSoft }
    ],
    series: [{
      name: '指数',
      type: 'candlestick',
      itemStyle: {
        color: up, color0: down,
        borderColor: up, borderColor0: down
      },
      data: props.points.map(point => [point.open, point.close, point.low, point.high])
    }]
  }, true)
}

async function sync() {
  await nextTick()
  if (props.loading || props.points.length === 0) {
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
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose() })
</script>

<template>
  <section class="card kline-chart">
    <header><div><h2>{{ title }}</h2><p>{{ subtitle }}</p></div></header>
    <div v-if="loading" class="chart-state" aria-live="polite"><div class="skeleton chart-skeleton"></div></div>
    <div v-else-if="points.length === 0" class="chart-state empty-state"><p>暂无 K 线数据，请确认 CSQAQ Token 与服务器出口 IP 已绑定。</p></div>
    <div v-else ref="el" class="chart-canvas" role="img" :aria-label="`${title} K线图`"></div>
  </section>
</template>

<style scoped>
.kline-chart { overflow: hidden; }
header { padding: 15px 18px 0; } h2 { margin: 0; font-size: 15px; } p { margin: 3px 0 0; color: var(--text-muted); font-size: 11px; }
.chart-canvas, .chart-state { height: 350px; padding: 6px 10px 8px; }.chart-skeleton { width: 100%; height: 100%; }.chart-state.empty-state { height: 260px; justify-content: center; }
</style>
