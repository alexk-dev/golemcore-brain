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

import { useState } from 'react'
import type { ChangeEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { applyMarkdownImport, planMarkdownImport } from '../../lib/api'
import { pathToRoute } from '../../lib/paths'
import { useTreeStore } from '../../stores/tree'
import type { WikiImportApplyResponse, WikiImportItem, WikiImportPlanResponse, WikiImportPolicy } from '../../types'

export function ImportPage() {
  const navigate = useNavigate()
  const reloadTree = useTreeStore((state) => state.reloadTree)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [targetRootPath, setTargetRootPath] = useState('')
  const [plan, setPlan] = useState<WikiImportPlanResponse | null>(null)
  const [planItems, setPlanItems] = useState<WikiImportItem[]>([])
  const [applyResult, setApplyResult] = useState<WikiImportApplyResponse | null>(null)

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null
    setSelectedFile(file)
    setPlan(null)
    setPlanItems([])
    setApplyResult(null)
  }

  const handlePreview = async () => {
    if (!selectedFile) {
      return
    }
    try {
      const nextPlan = await planMarkdownImport(selectedFile, { targetRootPath })
      setPlan(nextPlan)
      setPlanItems(nextPlan.items)
      setApplyResult(null)
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  const handleApply = async () => {
    if (!selectedFile) {
      return
    }
    try {
      const result = await applyMarkdownImport(selectedFile, {
        targetRootPath,
        items: planItems.map((item) => ({
          sourcePath: item.sourcePath,
          selected: item.selected,
          policy: item.policy,
        })),
      })
      setApplyResult(result)
      await reloadTree()
      if (result.importedCount > 0) {
        navigate(pathToRoute(result.importedRootPath))
      }
      toast.success(`Imported ${result.importedCount} item(s)`)
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  const updateItemSelection = (sourcePath: string, selected: boolean) => {
    setPlanItems((items) => items.map((item) => {
      if (item.sourcePath !== sourcePath || item.implicitSection) {
        return item
      }
      return { ...item, selected }
    }))
  }

  const updateItemPolicy = (sourcePath: string, policy: WikiImportPolicy) => {
    setPlanItems((items) => items.map((item) => (
      item.sourcePath === sourcePath ? { ...item, policy } : item
    )))
  }

  return (
    <div className="page-viewer">
      <div className="surface-card p-6">
        <h1 className="mb-4 text-2xl font-semibold">Import markdown archive</h1>
        <label className="field mb-4">
          <span className="text-sm font-medium">Archive file</span>
          <input className="field-input" type="file" accept=".zip" onChange={handleFileChange} />
        </label>
        <label className="field mb-4">
          <span className="text-sm font-medium">Target root path</span>
          <input
            className="field-input"
            placeholder="Import into wiki root"
            value={targetRootPath}
            onChange={(event) => {
              setTargetRootPath(event.target.value)
              setPlan(null)
              setPlanItems([])
              setApplyResult(null)
            }}
          />
        </label>
        <div className="flex gap-3">
          <button type="button" className="action-button-secondary" disabled={!selectedFile} onClick={() => void handlePreview()}>
            Preview import
          </button>
          <button type="button" className="action-button-primary" disabled={!selectedFile || !plan} onClick={() => void handleApply()}>
            Apply import
          </button>
        </div>

        {plan ? (
          <div className="mt-6 space-y-4">
            <h2 className="text-lg font-semibold">Import plan</h2>
            <div className="grid gap-3 md:grid-cols-3">
              <div className="rounded-2xl border border-surface-border bg-surface-alt/60 px-4 py-3 text-sm">
                Create: {plan.createCount}
              </div>
              <div className="rounded-2xl border border-surface-border bg-surface-alt/60 px-4 py-3 text-sm">
                Update: {plan.updateCount}
              </div>
              <div className="rounded-2xl border border-surface-border bg-surface-alt/60 px-4 py-3 text-sm">
                Skip: {plan.skipCount}
              </div>
            </div>
            {plan.warnings.length > 0 ? (
              <div className="rounded-2xl border border-warning/40 bg-warning/10 px-4 py-3 text-sm text-warning">
                {plan.warnings.map((warning) => (
                  <div key={warning}>{warning}</div>
                ))}
              </div>
            ) : null}
            {planItems.map((item) => (
              <div key={item.sourcePath} className="rounded-2xl border border-surface-border bg-surface-alt/60 px-4 py-3">
                <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                  <div className="min-w-0 flex-1">
                    <div className="font-medium">{item.path}</div>
                    <div className="text-sm text-muted">
                      {item.kind} · {item.action} · {item.sourcePath}
                    </div>
                    {item.note ? <div className="text-xs text-muted">{item.note}</div> : null}
                  </div>
                  <div className="flex flex-wrap items-center gap-3">
                    <label className="flex items-center gap-2 text-sm">
                      <input
                        type="checkbox"
                        checked={item.selected}
                        disabled={item.implicitSection}
                        onChange={(event) => updateItemSelection(item.sourcePath, event.target.checked)}
                      />
                      {item.implicitSection ? 'Auto' : 'Import'}
                    </label>
                    <select
                      className="field-input min-w-[11rem]"
                      value={item.policy}
                      disabled={!item.selected || !item.existing}
                      onChange={(event) => updateItemPolicy(item.sourcePath, event.target.value as WikiImportPolicy)}
                    >
                      <option value="OVERWRITE">Overwrite existing</option>
                      <option value="KEEP_EXISTING">Keep existing</option>
                    </select>
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : null}

        {applyResult ? (
          <div className="mt-6 space-y-3">
            <div className="text-sm text-muted">
              Imported {applyResult.importedCount} item(s). Created: {applyResult.createdCount}. Updated: {applyResult.updatedCount}. Skipped: {applyResult.skippedCount}.
            </div>
            {applyResult.warnings.length > 0 ? (
              <div className="rounded-2xl border border-warning/40 bg-warning/10 px-4 py-3 text-sm text-warning">
                {applyResult.warnings.map((warning) => (
                  <div key={warning}>{warning}</div>
                ))}
              </div>
            ) : null}
            <div className="text-xs text-muted">
              Navigated to: {applyResult.importedRootPath || '/'}
            </div>
          </div>
        ) : null}
      </div>
    </div>
  )
}
