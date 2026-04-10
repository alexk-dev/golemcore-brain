import { useState } from 'react'
import type { ChangeEvent } from 'react'
import { toast } from 'sonner'

import { applyMarkdownImport, planMarkdownImport } from '../../lib/api'
import type { WikiImportApplyResponse, WikiImportPlanResponse } from '../../types'

export function ImportPage() {
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [plan, setPlan] = useState<WikiImportPlanResponse | null>(null)
  const [applyResult, setApplyResult] = useState<WikiImportApplyResponse | null>(null)

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null
    setSelectedFile(file)
    setPlan(null)
    setApplyResult(null)
  }

  const handlePreview = async () => {
    if (!selectedFile) {
      return
    }
    try {
      const nextPlan = await planMarkdownImport(selectedFile)
      setPlan(nextPlan)
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
      const result = await applyMarkdownImport(selectedFile)
      setApplyResult(result)
      toast.success(`Imported ${result.importedCount} item(s)`)    
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  return (
    <div className="page-viewer">
      <div className="surface-card p-6">
        <h1 className="mb-4 text-2xl font-semibold">Import markdown archive</h1>
        <label className="field mb-4">
          <span className="text-sm font-medium">Archive file</span>
          <input className="field-input" type="file" accept=".zip" onChange={handleFileChange} />
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
          <div className="mt-6 space-y-2">
            <h2 className="text-lg font-semibold">Import plan</h2>
            {plan.items.map((item) => (
              <div key={item.path} className="rounded-2xl border border-surface-border bg-surface-alt/60 px-4 py-3">
                <div className="font-medium">{item.path}</div>
                <div className="text-sm text-muted">{item.kind} · {item.action} · {item.sourcePath}</div>
              </div>
            ))}
          </div>
        ) : null}

        {applyResult ? (
          <div className="mt-6 text-sm text-muted">
            Imported {applyResult.importedCount} item(s). Created: {applyResult.createdCount}. Updated: {applyResult.updatedCount}.
          </div>
        ) : null}
      </div>
    </div>
  )
}
