import { lazy, Suspense, useMemo } from 'react'

const LazyMermaidRenderer = lazy(() => import('./MermaidRenderer'))

interface MermaidBlockProps {
  code: string
  theme: 'default' | 'dark'
}

export function MermaidBlock({ code, theme }: MermaidBlockProps) {
  const fallback = useMemo(
    () => <div className="mermaid-block__loading">Rendering Mermaid diagram…</div>,
    [],
  )

  return (
    <Suspense fallback={fallback}>
      <LazyMermaidRenderer code={code} theme={theme} />
    </Suspense>
  )
}
