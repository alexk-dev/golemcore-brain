import { FilePlus } from 'lucide-react'
import { Link } from 'react-router-dom'

interface NotFoundPageProps {
  path: string
  onCreate: () => void
  canCreate?: boolean
  signInHref?: string | null
}

export function NotFoundPage({ path, onCreate, canCreate = true, signInHref = null }: NotFoundPageProps) {
  return (
    <div className="shell-form-page">
      <div className="shell-form-page__card--wide surface-card p-6">
        <h2 className="mb-2 text-xl font-semibold">Page not found</h2>
        <p className="mb-4 text-sm text-muted">
          The page <span className="font-mono">/{path}</span> does not exist yet.
        </p>
        {canCreate ? (
          <button type="button" className="action-button-primary" onClick={onCreate}>
            <FilePlus size={16} />
            Create page by path
          </button>
        ) : signInHref ? (
          <Link to={signInHref} className="action-button-primary">
            Sign in to create this page
          </Link>
        ) : (
          <p className="text-sm text-muted">You need edit access to create this page.</p>
        )}
      </div>
    </div>
  )
}
