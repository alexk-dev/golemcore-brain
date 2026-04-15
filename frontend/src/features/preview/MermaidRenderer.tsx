/*
 * Copyright 2026 Aleksei Kuleshov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contact: alex@kuleshov.tech
 */

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
