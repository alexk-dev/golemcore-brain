import { Link } from 'react-router-dom'

interface AccessDeniedPageProps {
  title?: string
  message?: string
  ctaLabel?: string
  ctaTo?: string
}

export function AccessDeniedPage({
  title = 'Access denied',
  message = 'You do not have permission to view this page.',
  ctaLabel,
  ctaTo,
}: AccessDeniedPageProps) {
  return (
    <div className="shell-form-page">
      <div className="shell-form-page__card--wide surface-card p-6">
        <h2 className="mb-2 text-xl font-semibold">{title}</h2>
        <p className="text-sm text-muted">{message}</p>
        {ctaLabel && ctaTo ? (
          <div className="mt-4">
            <Link to={ctaTo} className="action-button-primary">
              {ctaLabel}
            </Link>
          </div>
        ) : null}
      </div>
    </div>
  )
}
