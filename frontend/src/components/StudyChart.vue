<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'
echarts.use([BarChart, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])
const props = defineProps<{ option: EChartsCoreOption; label: string }>()
const element = ref<HTMLDivElement | null>(null)
let chart: echarts.EChartsType | undefined, resize: ResizeObserver | undefined, theme: MutationObserver | undefined
function styleOption(input: unknown, style: string, color: string): unknown {
  if (Array.isArray(input)) return input.map(value => styleOption(value, style, color))
  if (!input || typeof input !== 'object') return input
  const value = input as Record<string, unknown>
  return { ...value, [style]: { color, ...(value[style] as object || {}) } }
}
function render() {
  const theme = getComputedStyle(document.documentElement), color = theme.getPropertyValue('--muted').trim()
  const option = { ...props.option }
  if (option.xAxis) option.xAxis = styleOption(option.xAxis, 'axisLabel', color)
  if (option.yAxis) {
    option.yAxis = styleOption(option.yAxis, 'axisLabel', color)
    const axes = (Array.isArray(option.yAxis) ? option.yAxis : [option.yAxis]) as Record<string, unknown>[]
    option.yAxis = axes.map(axis => ({ ...axis, splitLine: { lineStyle: { color: theme.getPropertyValue('--border').trim(), type: 'dashed' }, ...(axis.splitLine as object || {}) } }))
  }
  if (option.legend) option.legend = styleOption(option.legend, 'textStyle', color)
  if (option.tooltip) {
    const tooltip = option.tooltip as Record<string, unknown>
    option.tooltip = { backgroundColor: theme.getPropertyValue('--panel').trim(), borderColor: theme.getPropertyValue('--border').trim(), ...tooltip, textStyle: { color, ...(tooltip.textStyle as object || {}) } }
  }
  chart?.setOption({ backgroundColor: 'transparent', animation: !matchMedia('(prefers-reduced-motion: reduce)').matches, color: ['#5386b0','#a4c5e2','#cf7955','#7f9eaa','#8b88ae','#a6b8c7'], textStyle: { color, fontFamily: theme.fontFamily }, ...option }, true)
}
onMounted(() => { if (element.value) { chart = echarts.init(element.value); render(); resize = new ResizeObserver(() => chart?.resize()); resize.observe(element.value); theme = new MutationObserver(render); theme.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] }) } })
watch(() => props.option, render, { deep: true })
onUnmounted(() => { resize?.disconnect(); theme?.disconnect(); chart?.dispose() })
</script>
<template><div ref="element" class="chart" role="img" :aria-label="label"></div></template>
