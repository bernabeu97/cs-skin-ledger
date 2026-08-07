<script setup lang="ts">
import * as echarts from 'echarts'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { PnlRow } from '../types'

const props = defineProps<{ rows: PnlRow[] }>()
const el = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function render() {
  if (!el.value) return
  if (!chart) chart = echarts.init(el.value)
  chart.setOption({
    title: { text: '月度已实现盈亏' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: props.rows.map(r => r.key) },
    yAxis: { type: 'value' },
    series: [{
      type: 'bar',
      data: props.rows.map(r => r.realizedPnl),
      itemStyle: { color: '#2563eb' }
    }]
  })
}

onMounted(render)
watch(() => props.rows, render, { deep: true })
onBeforeUnmount(() => chart?.dispose())
</script>

<template>
  <div ref="el" style="height: 320px; background: #fff; border-radius: 8px; margin-bottom: 24px;"></div>
</template>