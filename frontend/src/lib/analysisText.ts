// Older analyses contain prose and bare equations in one paragraph. Only adapt
// plain text; authored Markdown, links, code and TeX keep their original syntax.
export function isDisplayEquation(content: string): boolean {
  const arithmetic = /[+*/×÷^]|[\w)]\s*-|\\(?:frac|times|div)\b/.test(content)
  return arithmetic && /\d|[=≤≥<>≈]|\\(?:le|ge|leq|geq|approx)\b/.test(content)
}

export function formatAnalysisText(content: string): string {
  if (/[`$\\]|\*\*|__|\[[^\n]*\]|https?:\/\/|^\s{0,3}(?:#{1,6}\s|>\s|[-*+]\s|\d+[.)]\s)|\|.*\|/m.test(content)) return content

  const atom = '(?:[A-Za-z]|-?\\d+(?:\\.\\d+)?(?:[A-Za-z]|%)?)'
  const equation = new RegExp(`(?<![\\w/.:])${atom}(?:[ \\t]*[+\\-*/×÷=≈≤≥<>≠][ \\t]*${atom})+`, 'g')
  return content
    .replace(/([。！？])\s*(?=\S)/g, '$1\n\n')
    .replace(equation, value => /[=≈≤≥<>≠]/.test(value) ? `\n\n$$\n${value.trim().replaceAll('%', '\\%')}\n$$\n\n` : value)
    .replace(/(\$\$\n\n)[。；，、,;]\s*/g, '$1')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}
