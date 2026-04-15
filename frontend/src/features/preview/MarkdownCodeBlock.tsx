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

import copy from 'copy-to-clipboard'
import { Check, Copy } from 'lucide-react'
import { useEffect, useState, type HTMLAttributes, type ReactNode } from 'react'
import { toast } from 'sonner'

interface MarkdownCodeBlockProps extends HTMLAttributes<HTMLPreElement> {
  children?: ReactNode
  code: string
}

export function MarkdownCodeBlock({ children, code, ...props }: MarkdownCodeBlockProps) {
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!copied) {
      return
    }
    const timeoutId = window.setTimeout(() => setCopied(false), 1800)
    return () => window.clearTimeout(timeoutId)
  }, [copied])

  const handleCopy = () => {
    const didCopy = copy(code)
    if (!didCopy) {
      toast.error('Could not copy code')
      return
    }
    setCopied(true)
    toast.success('Code copied')
  }

  return (
    <div className="markdown-code-block custom-scrollbar">
      <div className="markdown-code-block__actions">
        <button
          type="button"
          className="markdown-code-block__copy-button"
          onClick={handleCopy}
          aria-label={copied ? 'Code copied' : 'Copy code'}
          data-testid="markdown-code-copy-button"
        >
          {copied ? <Check size={16} /> : <Copy size={16} />}
        </button>
      </div>
      <pre {...props}>{children}</pre>
    </div>
  )
}
