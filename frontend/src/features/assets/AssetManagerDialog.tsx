import { useCallback, useEffect, useState } from 'react'
import { ImagePlus, Link2, Pencil, Trash2 } from 'lucide-react'
import { toast } from 'sonner'

import { ModalCard } from '../../components/ModalCard'
import { deleteAsset, listAssets, renameAsset, uploadAsset } from '../../lib/api'
import type { WikiAsset } from '../../types'

interface AssetManagerDialogProps {
  open: boolean
  pagePath: string
  onOpenChange: (open: boolean) => void
  onInsertMarkdown: (markdown: string) => void
}

function buildMarkdownForAsset(asset: WikiAsset): string {
  if (asset.contentType.startsWith('image/')) {
    return `![${asset.name}](${asset.path})`
  }
  if (asset.contentType.startsWith('audio/')) {
    return `<audio controls src="${asset.path}"></audio>`
  }
  if (asset.contentType.startsWith('video/')) {
    return `<video controls src="${asset.path}"></video>`
  }
  return `[${asset.name}](${asset.path})`
}

export function AssetManagerDialog({
  open,
  pagePath,
  onOpenChange,
  onInsertMarkdown,
}: AssetManagerDialogProps) {
  const [assets, setAssets] = useState<WikiAsset[]>([])
  const [loading, setLoading] = useState(false)
  const [renameTarget, setRenameTarget] = useState<string | null>(null)
  const [renameValue, setRenameValue] = useState('')

  const reloadAssets = useCallback(async () => {
    if (!pagePath) {
      setAssets([])
      return
    }
    setLoading(true)
    try {
      const nextAssets = await listAssets(pagePath)
      setAssets(nextAssets)
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setLoading(false)
    }
  }, [pagePath])

  useEffect(() => {
    if (!open) {
      return
    }
    void reloadAssets()
  }, [open, reloadAssets])

  const handleUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file || !pagePath) {
      return
    }
    try {
      await uploadAsset(pagePath, file)
      toast.success(`Uploaded ${file.name}`)
      await reloadAssets()
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      event.target.value = ''
    }
  }

  const handleRename = async (asset: WikiAsset) => {
    try {
      await renameAsset(pagePath, asset.name, renameValue)
      toast.success('Asset renamed')
      setRenameTarget(null)
      setRenameValue('')
      await reloadAssets()
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  const handleDelete = async (asset: WikiAsset) => {
    try {
      await deleteAsset(pagePath, asset.name)
      toast.success('Asset deleted')
      await reloadAssets()
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  return (
    <ModalCard
      open={open}
      title="Asset manager"
      description={`Attach images, audio, video, or files to /${pagePath}`}
      onOpenChange={onOpenChange}
      footer={
        <button type="button" className="action-button-secondary" onClick={() => onOpenChange(false)}>
          Close
        </button>
      }
    >
      <label className="action-button-primary cursor-pointer">
        <ImagePlus size={16} />
        Upload asset
        <input type="file" className="hidden" onChange={handleUpload} />
      </label>

      <div className="space-y-3">
        {loading ? <div className="text-sm text-muted">Loading assets...</div> : null}
        {!loading && assets.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-surface-border bg-surface-alt/60 px-4 py-6 text-center text-sm text-muted">
            No assets uploaded yet.
          </div>
        ) : null}
        {assets.map((asset) => (
          <div key={asset.name} className="flex items-center justify-between gap-3 rounded-2xl border border-surface-border bg-surface-alt/60 px-4 py-3">
            <div className="min-w-0 flex-1">
              <div className="truncate text-sm font-medium text-foreground">{asset.name}</div>
              <div className="text-xs text-muted">{asset.contentType} · {Math.round(asset.size / 1024)} KB</div>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                className="action-button-secondary"
                onClick={() => onInsertMarkdown(buildMarkdownForAsset(asset))}
              >
                <Link2 size={16} />
                Insert
              </button>
              {renameTarget === asset.name ? (
                <div className="flex items-center gap-2">
                  <input
                    className="field-input w-40"
                    value={renameValue}
                    onChange={(event) => setRenameValue(event.target.value)}
                  />
                  <button type="button" className="action-button-primary" onClick={() => void handleRename(asset)}>
                    Save
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  className="action-button-secondary"
                  onClick={() => {
                    setRenameTarget(asset.name)
                    setRenameValue(asset.name)
                  }}
                >
                  <Pencil size={16} />
                </button>
              )}
              <button type="button" className="action-button-danger" onClick={() => void handleDelete(asset)}>
                <Trash2 size={16} />
              </button>
            </div>
          </div>
        ))}
      </div>
    </ModalCard>
  )
}
