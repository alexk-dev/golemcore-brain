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

import { Coffee, Compass, FilePlus, Home, RefreshCw } from 'lucide-react'
import { Link } from 'react-router-dom'

type ErrorPageVariant = 'not-found' | 'server-error'

interface ErrorPageProps {
  variant: ErrorPageVariant
  path?: string
  canCreate?: boolean
  signInHref?: string | null
  onCreate?: () => void
}

export function ErrorPage({ variant, path = '', canCreate = false, signInHref = null, onCreate }: ErrorPageProps) {
  const isNotFound = variant === 'not-found'
  const Icon = isNotFound ? Compass : Coffee
  const title = isNotFound ? 'Page not found' : 'Server had a wobble'
  const code = isNotFound ? '404' : '500'
  const message = isNotFound
    ? 'This page wandered off. We looked behind the sidebar, under the markdown, and inside the nearest folder. Nothing yet.'
    : 'Something went wrong on our side. The scary details are safely hidden while the neurons regroup.'
  const hint = isNotFound
    ? 'If this page should exist, you can create it and give it a proper home.'
    : 'Try refreshing the page. If it keeps happening, take a short tea break and try again.'

  return (
    <div className="shell-form-page">
      <section className="shell-form-page__card--wide surface-card overflow-hidden p-0" aria-labelledby="error-page-title">
        <div className="border-b border-surface-border bg-surface-alt/70 px-6 py-5">
          <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-accent/30 bg-accent/10 px-3 py-1 text-sm font-medium text-accent">
            <Icon size={16} /> Friendly error {code}
          </div>
          <h1 id="error-page-title" className="text-3xl font-bold tracking-tight text-foreground">
            {title}
          </h1>
        </div>
        <div className="space-y-5 px-6 py-6">
          <p className="text-base text-muted">{message}</p>
          {isNotFound && path ? (
            <div className="rounded-2xl border border-surface-border bg-background px-4 py-3 font-mono text-sm text-muted">
              /{path}
            </div>
          ) : null}
          <p className="text-sm text-muted">{hint}</p>
          <div className="flex flex-wrap gap-3">
            <Link to="/" className="action-button-secondary">
              <Home size={16} /> Back home
            </Link>
            {isNotFound && canCreate && onCreate ? (
              <button type="button" className="action-button-primary" onClick={onCreate}>
                <FilePlus size={16} /> Create page by path
              </button>
            ) : null}
            {isNotFound && !canCreate && signInHref ? (
              <Link to={signInHref} className="action-button-primary">
                Sign in to create this page
              </Link>
            ) : null}
            {!isNotFound ? (
              <button type="button" className="action-button-primary" onClick={() => window.location.reload()}>
                <RefreshCw size={16} /> Try again
              </button>
            ) : null}
          </div>
        </div>
      </section>
    </div>
  )
}
