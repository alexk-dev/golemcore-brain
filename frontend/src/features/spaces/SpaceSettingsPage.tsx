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
