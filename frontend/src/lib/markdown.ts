import MarkdownIt from 'markdown-it'
import { tex } from '@mdit/plugin-tex'
import katex from 'katex'
import DOMPurify from 'dompurify'

const markdown = new MarkdownIt({ html: false, breaks: true, linkify: true })

markdown.use(tex, {
  delimiters: 'all',
  mathFence: true,
  render(content: string, displayMode: boolean) {
    let formula: string
    try {
      formula = katex.renderToString(content, {
        displayMode, output: 'htmlAndMathml', trust: false, strict: 'ignore',
        throwOnError: true, maxExpand: 1000, maxSize: 10,
      })
    } catch {
      formula = '<code class="math-fallback">' + markdown.utils.escapeHtml(content) + '</code>'
    }
    return displayMode
      ? '<div class="math-block" tabindex="0" role="region" aria-label="公式">' + formula + '</div>\n'
      : '<span class="math-inline">' + formula + '</span>'
  },
})

const validateLink = markdown.validateLink.bind(markdown)
markdown.validateLink = url => validateLink(url) && /^(https?:\/\/|mailto:|\/(?!\/)|#)/i.test(url)
const renderLink = markdown.renderer.rules.link_open || ((tokens, index, options, _env, self) => self.renderToken(tokens, index, options))
markdown.renderer.rules.link_open = (tokens, index, options, env, self) => {
  tokens[index]!.attrSet('target', '_blank')
  tokens[index]!.attrSet('rel', 'noopener noreferrer')
  return renderLink(tokens, index, options, env, self)
}
// Replies can reference sources, but cannot load remote tracking images.
markdown.renderer.rules.image = (tokens, index) => markdown.utils.escapeHtml(tokens[index]!.content)
markdown.renderer.rules.table_open = () => '<div class="markdown-table-scroll" tabindex="0" role="region" aria-label="回答中的表格"><table>\n'
markdown.renderer.rules.table_close = () => '</table></div>\n'
for (const rule of ['fence', 'code_block']) {
  const renderCode = markdown.renderer.rules[rule]!
  markdown.renderer.rules[rule] = (tokens, index, options, env, self) =>
    renderCode(tokens, index, options, env, self).replace('<pre>', '<pre tabindex="0" aria-label="代码块">')
}

export function renderMarkdown(content: string): string {
  // Sanitize after all plugins; model responses and saved history are untrusted.
  return DOMPurify.sanitize(markdown.render(content), {
    USE_PROFILES: { html: true, mathMl: true },
    ADD_ATTR: ['target'],
    FORBID_TAGS: ['img', 'style', 'iframe', 'object', 'embed', 'form', 'input', 'button', 'video', 'audio'],
    ALLOW_DATA_ATTR: false,
  })
}
