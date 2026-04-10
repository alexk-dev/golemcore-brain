export function AccessDeniedPage() {
  return (
    <div className="page-editor__error">
      <div className="surface-card max-w-xl p-6">
        <h2 className="mb-2 text-xl font-semibold">Access denied</h2>
        <p className="text-sm text-muted">
          You do not have permission to view this page.
        </p>
      </div>
    </div>
  )
}
