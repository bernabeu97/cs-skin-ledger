<script setup lang="ts">
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { PnlRow } from '../types'
import { formatMoney, formatSignedMoney } from '../utils/format'

echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{ rows: PnlRow[]; loading: boolean; periodLabel: string }>()
const el = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let observer: ResizeObserver | null = null
let chartEl: HTMLDivElement | null = null

function render() {
  if (!el.value) return
  if (chartEl !== el.value) {
    observer?.disconnect()
    chart?.dispose()
    chartEl = el.value
    chart = echarts.init(chartEl)
    observer = new ResizeObserver(() => chart?.resize())
    observer.observe(chartEl)
  }
  chart?.setOption({
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value: unknown) => formatMoney(Number(value))
    },
    grid: { left: 12, right: 20, top: 28, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: props.rows.map(r => r.key),
      axisLine: { lineStyle: { color: '#d0d5dd' } },
      axisLabel: { color: '#565d6b', fontFamily: 'inherit' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#eef0f3' } },
      axisLabel: { color: '#8a91a0', formatter: (v: number) => formatMoney(v) }
    },
    series: [{
      type: 'bar',
      barMaxWidth: 36,
      data: props.rows.map(r => ({
        value: r.realizedPnl,
        itemStyle: { color: r.realizedPnl >= 0 ? '#2563eb' : '#c62f3f', borderRadius: [3, 3, 0, 0] }
      })),
      label: {
        show: true,
        position: 'top',
        color: '#565d6b',
        fontSize: 11,
        formatter: (p: { value: number }) => formatSignedMoney(p.value)
      }
    }]
  }, true)
}

async function syncChart() {
  await nextTick()
  render()
}

onMounted(syncChart)
watch([() => props.loading, () => props.rows], syncChart, { deep: true, flush: 'post' })
onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = null
  chartEl = null
})
</script>

<template>
  <div class="card chart-card">
    <div class="chart-head">
      <div>
        <h2>{{ periodLabel }}已实现盈亏</h2>
        <span class="chart-sub">单位：CNY · 已扣除手续费</span>
      </div>
    </div>
    <div v-if="loading" class="chart-body">
      <div class="skeleton" style="height:100%;width:100%"></div>
    </div>
    <div v-else-if="rows.length === 0" class="chart-body empty-state">
      <div class="empty-icon" aria-hidden="true">📈</div>
      <p>该周期暂无盈亏数据，先去「交易记录」录入交易。</p>
    </div>
    <div v-else ref="el" class="chart-body"></div>
  </div>
</template>

<style scoped>
.chart-card { margin-bottom: 24px; overflow: hidden; }
.chart-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px 0;
}
.chart-head h2 { margin: 0; }
.chart-sub { font-size: 12px; color: var(--text-muted); }
.chart-body { height: 300px; padding: 8px 12px 12px; }
.chart-body.empty-state { height: 220px; }
</style>
