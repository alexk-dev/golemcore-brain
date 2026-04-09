import { FilePlus } from 'lucide-react'

interface NotFoundPageProps {
  path: string
  onCreate: () => void
}

export function NotFoundPage({ path, onCreate }: NotFoundPageProps) {
  return (
    <div className="page-editor__error">
      <div className="surface-card max-w-xl p-6">
        <h2 className="mb-2 text-xl font-semibold">Page not found</h2>
        <p className="mb-4 text-sm text-muted">
          The page <span className="font-mono">/{path}</span> does not exist yet.
        </p>
        <button type="button" className="action-button-primary" onClick={onCreate}>
          <FilePlus size={16} />
          Create page by path
        </button>
      </div>
    </div>
  )
}
