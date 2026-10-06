import { describe, expect, it } from 'vitest'
import { formatAnalysisText } from './analysisText'

describe('legacy analysis formatting', () => {
  it('separates explanations and complete equations without changing values', () => {
    const result = formatAnalysisText('设甲x件、乙y件。利润P=1000x+1700y。约束为2x+5y≤200、3x+4y≤240，x、y为非负整数。选C。')
    expect(result).toContain('设甲x件、乙y件。\n\n利润')
    for (const equation of ['P=1000x+1700y', '2x+5y≤200', '3x+4y≤240']) expect(result).toContain(`$$\n${equation}\n$$`)
    expect(result).toContain('x、y为非负整数。\n\n选C。')
  })
  it('keeps decimals, negative values and chained calculations intact', () => {
    const result = formatAnalysisText('检验：2×57+5×17=199≤200。交点x=400/7≈57.14。差值y=-1.25。')
    for (const equation of ['2×57+5×17=199≤200', 'x=400/7≈57.14', 'y=-1.25']) expect(result).toContain(`$$\n${equation}\n$$`)
  })
  it('keeps percentage units inside the equation and escapes them for TeX', () => {
    expect(formatAnalysisText('增长率：20/100×100%=20%。')).toBe('增长率：\n\n$$\n20/100×100\\%=20\\%\n$$')
    expect(formatAnalysisText('x=58,y=16。')).not.toContain('$$\n\n,')
  })
  it.each([
    '**读题**\n\n先看条件。\n\n$$\n2x+5y\\le200\n$$',
    '变量 $x$ 表示数量，$y$ 也是整数。',
    '\\[P=1000x+1700y\\]',
    '```text\nP=1000x+1700y\n```',
    '1. 理解条件。\n2. 计算答案。',
    '[资料](https://example.com?q=a=b)',
    '链接 https://example.com/a=b，日期2026/10/6。',
    '|条件|算式|\n|---|---|\n|甲|x=1|',
  ])('preserves existing Markdown, code, links and math: %s', content => {
    expect(formatAnalysisText(content)).toBe(content)
  })
  it('does not invent equations from dates, product names or arithmetic without a relation', () => {
    const content = '2026/10/6，型号A59，计算2×57+5×17，选择C。'
    expect(formatAnalysisText(content)).toBe(content)
  })
})
