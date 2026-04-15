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
