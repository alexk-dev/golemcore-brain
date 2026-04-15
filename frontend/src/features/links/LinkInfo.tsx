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

import { useEffect, useMemo, useState } from 'react'
import { Link2, Link2Off } from 'lucide-react'
import { Link as RouterLink } from 'react-router-dom'
import { toast } from 'sonner'

import { ModalCard } from '../../components/ModalCard'
import { getPageHistoryVersion, restorePageHistory } from '../../lib/api'
import { pathToRoute } from '../../lib/paths'
import { useTreeStore } from '../../stores/tree'
import { useUiStore } from '../../stores/ui'
import { useViewerStore } from '../../stores/viewer'
import type { WikiPageHistoryVersion } from '../../types'
import { MarkdownPreview } from '../preview/MarkdownPreview'
import { buildLineDiff } from './historyDiff'

function formatTimestamp(timestamp?: string) {
  if (!timestamp) {
    return 'Unknown time'
  }
  return new Date(timestamp).toLocaleString()
}

export function LinkInfo() {
  const page = useViewerStore((state) => state.page)
  const linkStatus = useViewerStore((state) => state.linkStatus)
  const history = useViewerStore((state) => state.history)
  const refreshPageData = useViewerStore((state) => state.refreshPageData)
  const reloadTree = useTreeStore((state) => state.reloadTree)
  const currentUser = useUiStore((state) => state.currentUser)
  const authDisabled = useUiStore((state) => state.authDisabled)
  const canRestore = authDisabled || currentUser?.role === 'ADMIN' || currentUser?.role === 'EDITOR'
  const [previewOpen, setPreviewOpen] = useState(false)
  const [restoreCandidate, setRestoreCandidate] = useState<string | null>(null)
  const [previewVersionId, setPreviewVersionId] = useState<string | null>(null)
  const [compareVersionId, setCompareVersionId] = useState<string>('current')
  const [versionCache, setVersionCache] = useState<Record<string, WikiPageHistoryVersion>>({})
  const previewVersion = previewVersionId ? versionCache[previewVersionId] ?? null : null
  const compareVersion = compareVersionId === 'current'
    ? page
    : versionCache[compareVersionId] ?? null

  useEffect(() => {
    if (!page) {
      setPreviewOpen(false)
      setPreviewVersionId(null)
      setCompareVersionId('current')
      setVersionCache({})
    }
  }, [page])

  const handleRestore = async (versionId: string) => {
    if (!page) {
      return
    }
    try {
      const restoredPage = await restorePageHistory(page.path, versionId)
      await reloadTree()
      await refreshPageData(restoredPage.path)
      toast.success('Version restored')
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  const handlePreview = async (versionId: string) => {
    if (!page) {
      return
    }
    try {
      const nextVersion = versionCache[versionId] ?? await getPageHistoryVersion(page.path, versionId)
      setVersionCache((state) => ({ ...state, [versionId]: nextVersion }))
      setPreviewVersionId(versionId)
      setCompareVersionId('current')
      setPreviewOpen(true)
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  useEffect(() => {
    if (!page || compareVersionId === 'current' || versionCache[compareVersionId]) {
      return
    }
    void getPageHistoryVersion(page.path, compareVersionId)
      .then((version) => {
        setVersionCache((state) => ({ ...state, [compareVersionId]: version }))
      })
      .catch((error: Error) => {
        toast.error(error.message)
      })
  }, [compareVersionId, page, versionCache])

  const diffLines = useMemo(() => {
    if (!previewVersion || !compareVersion) {
      return []
    }
    return buildLineDiff(compareVersion.content, previewVersion.content)
  }, [compareVersion, previewVersion])

  const diffSummary = useMemo(() => ({
    added: diffLines.filter((line) => line.type === 'added').length,
    removed: diffLines.filter((line) => line.type === 'removed').length,
  }), [diffLines])

  if (!linkStatus) {
    return null
  }

  return (
    <div className="backlinks__pane">
      <div className="backlinks__content">
        <div className="backlinks__group">
          <div className="backlinks__group-title">Backlinks</div>
          {linkStatus.backlinks.length === 0 ? (
            <p className="backlinks__empty">No pages reference this page.</p>
          ) : (
            <ul>
              {linkStatus.backlinks.map((item) => (
                <li key={`${item.fromPageId}-${item.toPath}`} className="backlinks__item">
                  {item.fromPath ? (
                    <RouterLink to={pathToRoute(item.fromPath)}>{item.fromTitle ?? item.fromPath}</RouterLink>
                  ) : (
                    <span>{item.fromTitle ?? 'Unknown page'}</span>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="backlinks__group">
          <div className="backlinks__group-title">Outgoing links</div>
          {linkStatus.outgoings.length === 0 && linkStatus.brokenOutgoings.length === 0 ? (
            <p className="backlinks__empty">No outgoing links on this page.</p>
          ) : (
            <ul>
              {linkStatus.outgoings.map((item) => (
                <li key={`${item.fromPageId}-${item.toPath}`} className="backlinks__item">
                  <Link2 className="backlinks__icon" size={14} />
                  {item.toPath ? (
                    <RouterLink to={pathToRoute(item.toPath)}>{item.toTitle ?? item.toPath}</RouterLink>
                  ) : (
                    <span>{item.toTitle ?? 'Unknown page'}</span>
                  )}
                </li>
              ))}
              {linkStatus.brokenOutgoings.map((item) => (
                <li key={`${item.fromPageId}-${item.toPath}`} className="backlinks__item backlinks__item--broken">
                  <Link2Off className="backlinks__icon" size={14} />
                  <span>{item.toTitle}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="backlinks__group">
          <div className="backlinks__group-title">Version history</div>
          {history.length === 0 ? (
            <p className="backlinks__empty">No previous versions recorded.</p>
          ) : (
            <ul>
              {history.map((entry) => (
                <li key={entry.id} className="backlinks__item flex items-center justify-between gap-2">
                  <div className="min-w-0">
                    <div>{entry.title}</div>
                    <div className="text-xs text-muted">
                      {formatTimestamp(entry.recordedAt)}
                      {entry.author ? ` · ${entry.author}` : ''}
                    </div>
                    {entry.reason || entry.summary ? (
                      <div className="text-xs text-muted">
                        {[entry.reason, entry.summary].filter(Boolean).join(' · ')}
                      </div>
                    ) : null}
                  </div>
                  <div className="flex shrink-0 gap-2">
                    <button type="button" className="action-button-secondary" onClick={() => void handlePreview(entry.id)}>
                      Preview
                    </button>
                    {canRestore ? (
                      <button type="button" className="action-button-secondary" onClick={() => setRestoreCandidate(entry.id)}>
                        Restore
                      </button>
                    ) : null}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      <ModalCard
        open={previewOpen}
        title={previewVersion ? `Version preview: ${previewVersion.title}` : 'Version preview'}
        description={previewVersion ? `${formatTimestamp(previewVersion.recordedAt)}${previewVersion.author ? ` · ${previewVersion.author}` : ''}` : undefined}
        onOpenChange={setPreviewOpen}
      >
        {previewVersion ? (
          <>
            <div className="rounded-2xl border border-surface-border bg-surface-alt/50 p-4 text-sm text-muted">
              {[previewVersion.reason, previewVersion.summary].filter(Boolean).join(' · ') || 'Saved history snapshot'}
            </div>
            <div className="rounded-2xl border border-surface-border bg-surface-alt/50 p-4">
              <div className="mb-3 flex items-center justify-between gap-3">
                <div className="text-sm font-medium">Compare against</div>
                <select
                  className="field-input max-w-xs"
                  value={compareVersionId}
                  onChange={(event) => setCompareVersionId(event.target.value)}
                >
                  <option value="current">Current page</option>
                  {history
                    .filter((entry) => entry.id !== previewVersion.id)
                    .map((entry) => (
                      <option key={entry.id} value={entry.id}>
                        {entry.title}
                      </option>
                    ))}
                </select>
              </div>
              <div className="mb-3 text-xs text-muted">
                Added lines: {diffSummary.added}. Removed lines: {diffSummary.removed}.
              </div>
              <div className="max-h-64 overflow-auto rounded-xl border border-surface-border bg-background p-3 font-mono text-xs">
                {diffLines.map((line, index) => (
                  <div
                    key={`${line.type}-${index}-${line.text}`}
                    className={
                      line.type === 'added'
                        ? 'bg-accent/10 text-accent'
                        : line.type === 'removed'
                          ? 'bg-danger/10 text-danger'
                          : 'text-muted'
                    }
                  >
                    <span className="mr-2 inline-block w-4">
                      {line.type === 'added' ? '+' : line.type === 'removed' ? '-' : ' '}
                    </span>
                    {line.text || ' '}
                  </div>
                ))}
              </div>
            </div>
            <div className="rounded-2xl border border-surface-border bg-surface-alt/50 p-4">
              <div className="mb-3 text-sm font-medium">Rendered preview</div>
              <div className="max-h-80 overflow-auto">
                <MarkdownPreview content={previewVersion.content} path={page?.path} darkMode={true} />
              </div>
            </div>
          </>
        ) : null}
      </ModalCard>

      <ModalCard
        open={restoreCandidate !== null}
        title="Restore version"
        description="Restore this snapshot and replace the current page content."
        onOpenChange={(open) => {
          if (!open) {
            setRestoreCandidate(null)
          }
        }}
        footer={(
          <>
            <button type="button" className="action-button-secondary" onClick={() => setRestoreCandidate(null)}>
              Cancel
            </button>
            <button
              type="button"
              className="action-button-danger"
              onClick={async () => {
                if (!restoreCandidate) {
                  return
                }
                await handleRestore(restoreCandidate)
                setRestoreCandidate(null)
              }}
            >
              Restore version
            </button>
          </>
        )}
      >
        <p className="text-sm text-muted">
          Your current page will be snapshotted into history before the restore is applied.
        </p>
      </ModalCard>
    </div>
  )
}
