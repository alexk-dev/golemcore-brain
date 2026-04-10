import { type ClassAttributes, type HTMLAttributes, type ReactNode, useMemo } from 'react'
import type { JSX } from 'react'
import ReactMarkdown from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import rehypeRaw from 'rehype-raw'
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize'
import remarkGfm from 'remark-gfm'

import { MarkdownCodeBlock } from './MarkdownCodeBlock'
import { MarkdownLink } from './MarkdownLink'
import { MermaidBlock } from './MermaidBlock'

interface MarkdownPreviewProps {
  content: string
  path?: string
  darkMode: boolean
  assetVersion?: number
}

const schema = {
  ...defaultSchema,
  tagNames: [...(defaultSchema.tagNames || []), 'audio', 'video'],
  attributes: {
    ...defaultSchema.attributes,
    '*': [...(defaultSchema.attributes?.['*'] || []), 'data-line', 'style'],
    audio: [...(defaultSchema.attributes?.audio || []), 'controls', 'src'],
    video: [...(defaultSchema.attributes?.video || []), 'controls', 'src', 'preload'],
  },
}

function readTextContent(node: ReactNode): string {
  if (typeof node === 'string' || typeof node === 'number') {
    return String(node)
  }
  if (Array.isArray(node)) {
    return node.map(readTextContent).join('')
  }
  if (node && typeof node === 'object' && 'props' in node) {
    return readTextContent((node as { props: { children?: ReactNode } }).props.children)
  }
  return ''
}

function normalizeAssetMediaSrc(src?: string, assetVersion?: number) {
  if (!src) {
    return src
  }
  if (!src.startsWith('/api/assets') || !assetVersion) {
    return src
  }
  return `${src}${src.includes('?') ? '&' : '?'}v=${assetVersion}`
}

export function MarkdownPreview({ content, path, darkMode, assetVersion }: MarkdownPreviewProps) {
  const components = useMemo(
    () => ({
      a: (
        props: ClassAttributes<HTMLAnchorElement> & HTMLAttributes<HTMLAnchorElement>,
      ) => <MarkdownLink currentPath={path} {...props} />,
      audio: (props: React.AudioHTMLAttributes<HTMLAudioElement>) => (
        <audio {...props} src={normalizeAssetMediaSrc(props.src, assetVersion)} />
      ),
      img: (props: React.ImgHTMLAttributes<HTMLImageElement>) => (
        <img {...props} src={normalizeAssetMediaSrc(props.src, assetVersion)} />
      ),
      video: (props: React.VideoHTMLAttributes<HTMLVideoElement>) => (
        <video {...props} src={normalizeAssetMediaSrc(props.src, assetVersion)} />
      ),
      pre: (
        props: ClassAttributes<HTMLPreElement> &
          HTMLAttributes<HTMLPreElement> & { children?: ReactNode },
      ) => {
        const child = Array.isArray(props.children) ? props.children[0] : props.children
        const code = readTextContent(child)
        return <MarkdownCodeBlock {...props} code={code} />
      },
      code: (
        props: JSX.IntrinsicAttributes &
          ClassAttributes<HTMLElement> &
          HTMLAttributes<HTMLElement>,
      ) => {
        const className = props.className ?? ''
        const rawCode = readTextContent(props.children)
        if (className.includes('language-mermaid')) {
          return (
            <MermaidBlock
              code={rawCode.trim()}
              theme={darkMode ? 'dark' : 'default'}
            />
          )
        }
        if (className.includes('language-')) {
          return <code {...props} />
        }
        return <code className="inline-code">{props.children}</code>
      },
    }),
    [assetVersion, darkMode, path],
  )

  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      rehypePlugins={[rehypeRaw, [rehypeSanitize, schema], rehypeHighlight]}
      components={components}
    >
      {content}
    </ReactMarkdown>
  )
}
