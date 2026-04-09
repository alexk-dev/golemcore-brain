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
