import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { chatWithSpace } from '../../lib/api'
import { pathToRoute } from '../../lib/paths'
import { useSpaceStore } from '../../stores/space'
import type { SpaceChatMessage, SpaceChatSource } from '../../types'

export function SpaceChatPage() {
  const activeSlug = useSpaceStore((state) => state.activeSlug)
  const [messages, setMessages] = useState<SpaceChatMessage[]>([])
  const [sources, setSources] = useState<SpaceChatSource[]>([])
  const [draft, setDraft] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [summary, setSummary] = useState<string | null>(null)
  const [turnCount, setTurnCount] = useState(0)

  const canSend = draft.trim().length > 0 && !isSending

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const question = draft.trim()
    if (!question || isSending) {
      return
    }
    const nextMessages: SpaceChatMessage[] = [...messages, { role: 'user', content: question }]
    setMessages(nextMessages)
    setDraft('')
    setIsSending(true)
    try {
      const nextTurnCount = turnCount + 1
      const response = await chatWithSpace(question, messages.slice(-12), undefined, summary, nextTurnCount)
      setMessages([...nextMessages, { role: 'assistant', content: response.answer }])
      setSources(response.sources)
      setSummary(response.summary ?? summary)
      setTurnCount(nextTurnCount)
    } catch (error) {
      toast.error((error as Error).message)
      setMessages(messages)
    } finally {
      setIsSending(false)
    }
  }

  return (
    <div className="page-viewer">
      <div className="surface-card flex min-h-[calc(100dvh-9rem)] flex-col p-6">
        <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="mb-1 text-2xl font-semibold">Chat with {activeSlug}</h1>
            <p className="text-sm text-muted">
              Ask questions against the current space knowledge base. Answers use the configured enabled chat model.
            </p>
          </div>
          <button
            type="button"
            className="action-button-secondary"
            onClick={() => {
              setMessages([])
              setSources([])
              setSummary(null)
              setTurnCount(0)
            }}
          >
            New chat
          </button>
        </div>

        <div className="flex min-h-0 flex-1 flex-col gap-4">
          <div className="custom-scrollbar flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto rounded-2xl border border-surface-border bg-surface-alt/40 p-4">
            {messages.length === 0 ? (
              <div className="m-auto max-w-xl text-center">
                <div className="text-lg font-semibold">Ask a question about this space</div>
                <p className="mt-2 text-sm text-muted">
                  Example: “What does our roadmap say?” or “Summarize the incident runbook.”
                </p>
              </div>
            ) : (
              messages.map((message, index) => (
                <div key={`${message.role}-${index}`} className={message.role === 'user' ? 'flex justify-end' : 'flex justify-start'}>
                  <div className={message.role === 'user'
                    ? 'max-w-3xl rounded-2xl bg-accent px-4 py-3 text-sm text-accent-foreground'
                    : 'max-w-3xl rounded-2xl border border-surface-border bg-background px-4 py-3 text-sm'}>
                    <div className="mb-1 text-xs font-semibold uppercase tracking-wide opacity-70">
                      {message.role === 'user' ? 'You' : 'Brain'}
                    </div>
                    <div className="whitespace-pre-wrap leading-relaxed">{message.content}</div>
                  </div>
                </div>
              ))
            )}
            {isSending ? <div className="text-sm text-muted">Thinking…</div> : null}
          </div>

          {sources.length > 0 ? (
            <section className="rounded-2xl border border-surface-border bg-surface-alt/50 p-4">
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted">Sources</h2>
              <div className="flex flex-wrap gap-2">
                {sources.map((source) => (
                  <Link key={source.path} className="action-button-secondary" to={pathToRoute(source.path)} title={source.excerpt}>
                    {source.title || source.path || 'Home'}
                  </Link>
                ))}
              </div>
            </section>
          ) : null}

          <form className="flex flex-col gap-3 md:flex-row" onSubmit={handleSubmit}>
            <label className="field flex-1">
              <span className="sr-only">Question</span>
              <textarea
                className="field-input min-h-24 resize-y"
                value={draft}
                placeholder="Ask a question about this space…"
                onChange={(event) => setDraft(event.target.value)}
              />
            </label>
            <div className="flex items-end">
              <button type="submit" className="action-button-primary w-full md:w-auto" disabled={!canSend}>
                {isSending ? 'Sending…' : 'Send'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}
