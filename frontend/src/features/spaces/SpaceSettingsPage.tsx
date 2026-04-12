import { Navigate, useParams } from 'react-router-dom'

import { DynamicSpaceApisPage } from '../dynamic-apis/DynamicSpaceApisPage'

export function SpaceSettingsPage() {
  const { spaceSlug } = useParams<{ spaceSlug: string }>()

  if (!spaceSlug) {
    return <Navigate to="/spaces" replace />
  }

  return (
    <div className="page-viewer">
      <div className="mb-4 surface-card p-6">
        <h1 className="mb-1 text-2xl font-semibold">Space settings</h1>
        <p className="text-sm text-muted">
          Configure settings that belong only to the <code>{spaceSlug}</code> space.
        </p>
      </div>
      <DynamicSpaceApisPage embedded spaceSlug={spaceSlug} />
    </div>
  )
}
