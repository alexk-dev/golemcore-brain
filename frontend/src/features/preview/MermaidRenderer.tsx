import mermaid from 'mermaid'
import { useEffect, useRef, useState } from 'react'

interface MermaidRendererProps {
  code: string
  theme: 'default' | 'dark'
}

export default function MermaidRenderer({ code, theme }: MermaidRendererProps) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    const renderMermaid = async () => {
      if (!containerRef.current) {
        return
      }
      try {
        mermaid.initialize({
          startOnLoad: false,
          theme,
          securityLevel: 'loose',
          deterministicIds: true,
          deterministicIDSeed: 'brain',
        })
        const renderId = `mermaid-${Math.random().toString(36).slice(2)}`
        const { svg } = await mermaid.render(renderId, code)
        if (!cancelled && containerRef.current) {
          containerRef.current.innerHTML = svg
          setError(null)
        }
      } catch (renderError) {
        if (!cancelled) {
          setError((renderError as Error).message)
        }
      }
    }

    void renderMermaid()
    return () => {
      cancelled = true
    }
  }, [code, theme])

  if (error) {
    return <div className="mermaid-block__error">Mermaid render failed: {error}</div>
  }

  return <div ref={containerRef} className="mermaid-block" />
}
