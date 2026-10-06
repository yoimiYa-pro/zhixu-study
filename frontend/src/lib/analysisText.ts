// Older analyses contain prose and bare equations in one paragraph. Only adapt
// plain text; authored Markdown, links, code and TeX keep their original syntax.
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
