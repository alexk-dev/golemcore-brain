import { useCallback, useEffect, useRef, useState } from 'react'
import { FileAudio, FileImage, FileVideo, ImagePlus, Link2, Pencil, Trash2, UploadCloud } from 'lucide-react'
import { toast } from 'sonner'

import { ModalCard } from '../../components/ModalCard'
import { deleteAsset, listAssets, renameAsset, uploadAsset } from '../../lib/api'
import type { WikiAsset } from '../../types'
import { buildDefaultMarkdownForAsset, buildImageMarkdown, buildLinkMarkdown, buildMediaMarkdown } from './assetMarkdown'

interface AssetManagerDialogProps {
  open: boolean
  pagePath: string
  onOpenChange: (open: boolean) => void
  onInsertMarkdown: (markdown: string) => void
  onAssetRenamed?: (oldAsset: WikiAsset, newAsset: WikiAsset) => void
  onAssetsChanged?: () => void
}

export function AssetManagerDialog({
  open,
  pagePath,
  onOpenChange,
  onInsertMarkdown,
  onAssetRenamed,
  onAssetsChanged,
}: AssetManagerDialogProps) {
  const [assets, setAssets] = useState<WikiAsset[]>([])
  const [loading, setLoading] = useState(false)
  const [renameTarget, setRenameTarget] = useState<string | null>(null)
  const [renameValue, setRenameValue] = useState('')
  const [isDragging, setIsDragging] = useState(false)
  const [uploadingFiles, setUploadingFiles] = useState<Record<string, 'uploading' | 'done' | 'error'>>({})
  const fileInputRef = useRef<HTMLInputElement | null>(null)

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

  const uploadFiles = async (files: File[]) => {
    if (files.length === 0 || !pagePath) {
      return
    }

    setUploadingFiles((currentFiles) => {
      const nextFiles = { ...currentFiles }
      files.forEach((file) => {
        nextFiles[file.name] = 'uploading'
      })
      return nextFiles
    })

    let uploadedCount = 0
    await Promise.all(files.map(async (file) => {
      try {
        await uploadAsset(pagePath, file)
        uploadedCount += 1
        setUploadingFiles((currentFiles) => ({ ...currentFiles, [file.name]: 'done' }))
      } catch (error) {
        setUploadingFiles((currentFiles) => ({ ...currentFiles, [file.name]: 'error' }))
        toast.error(`${file.name}: ${(error as Error).message}`)
      }
    }))

    if (uploadedCount > 0) {
      toast.success(`Uploaded ${uploadedCount} asset${uploadedCount === 1 ? '' : 's'}`)
      onAssetsChanged?.()
      await reloadAssets()
    }
  }

  const handleUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    try {
      await uploadFiles(Array.from(event.target.files ?? []))
    } finally {
      event.target.value = ''
    }
  }

  const handleRename = async (asset: WikiAsset) => {
    try {
      const renamedAsset = await renameAsset(pagePath, asset.name, renameValue)
      toast.success('Asset renamed')
      onAssetRenamed?.(asset, renamedAsset)
      onAssetsChanged?.()
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
      onAssetsChanged?.()
      await reloadAssets()
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  const renderInsertButtons = (asset: WikiAsset) => {
    if (asset.contentType.startsWith('image/')) {
      return (
        <>
          <button
            type="button"
            className="action-button-secondary"
            onClick={() => onInsertMarkdown(buildImageMarkdown(asset))}
            aria-label={`Insert ${asset.name} as image`}
          >
            <FileImage size={16} />
            Image
          </button>
          <button
            type="button"
            className="action-button-secondary"
            onClick={() => onInsertMarkdown(buildLinkMarkdown(asset))}
            aria-label={`Insert ${asset.name} as link`}
          >
            <Link2 size={16} />
            Link
          </button>
        </>
      )
    }
    if (asset.contentType.startsWith('audio/') || asset.contentType.startsWith('video/')) {
      return (
        <>
          <button
            type="button"
            className="action-button-secondary"
            onClick={() => onInsertMarkdown(buildMediaMarkdown(asset))}
            aria-label={`Insert ${asset.name} as media`}
          >
            {asset.contentType.startsWith('audio/') ? <FileAudio size={16} /> : <FileVideo size={16} />}
            Media
          </button>
          <button
            type="button"
            className="action-button-secondary"
            onClick={() => onInsertMarkdown(buildLinkMarkdown(asset))}
            aria-label={`Insert ${asset.name} as link`}
          >
            <Link2 size={16} />
            Link
          </button>
        </>
      )
    }
    return (
      <button
        type="button"
        className="action-button-secondary"
        onClick={() => onInsertMarkdown(buildDefaultMarkdownForAsset(asset))}
        aria-label={`Insert ${asset.name} as link`}
      >
        <Link2 size={16} />
        Insert
      </button>
    )
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
      <div
        className={`flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border border-dashed px-4 py-5 text-center transition ${isDragging ? 'border-accent bg-accent/10 text-accent' : 'border-surface-border bg-surface-alt/60 text-muted'}`}
        role="button"
        tabIndex={0}
        onClick={() => fileInputRef.current?.click()}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault()
            fileInputRef.current?.click()
          }
        }}
        onDragOver={(event) => {
          event.preventDefault()
          setIsDragging(true)
        }}
        onDragEnter={(event) => {
          event.preventDefault()
          setIsDragging(true)
        }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={(event) => {
          event.preventDefault()
          setIsDragging(false)
          void uploadFiles(Array.from(event.dataTransfer.files ?? []))
        }}
      >
        <UploadCloud size={20} />
        <div className="text-sm font-medium text-foreground">Drop files here or click to upload</div>
        <div className="text-xs text-muted">Images, audio, video, and files can be uploaded together.</div>
        <input ref={fileInputRef} type="file" className="hidden" multiple onChange={handleUpload} />
      </div>

      <button type="button" className="action-button-primary" onClick={() => fileInputRef.current?.click()}>
        <ImagePlus size={16} />
        Upload assets
      </button>

      {Object.keys(uploadingFiles).length > 0 ? (
        <div className="space-y-1 rounded-lg border border-surface-border bg-surface-alt/60 px-3 py-2 text-xs">
          {Object.entries(uploadingFiles).map(([fileName, status]) => (
            <div key={fileName} className="flex items-center justify-between gap-3">
              <span className="truncate">{fileName}</span>
              <span className={status === 'error' ? 'text-danger' : status === 'done' ? 'text-accent' : 'text-muted'}>
                {status === 'uploading' ? 'Uploading...' : status === 'done' ? 'Done' : 'Failed'}
              </span>
            </div>
          ))}
        </div>
      ) : null}

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
              <div className="mt-2">
                <a
                  className="text-xs text-accent hover:underline"
                  href={asset.path}
                  target="_blank"
                  rel="noreferrer"
                  aria-label={`Preview ${asset.name}`}
                >
                  Preview
                </a>
              </div>
            </div>
            <div className="flex items-center gap-2">
              {renderInsertButtons(asset)}
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
                  aria-label={`Rename ${asset.name}`}
                >
                  <Pencil size={16} />
                </button>
              )}
              <button
                type="button"
                className="action-button-danger"
                onClick={() => void handleDelete(asset)}
                aria-label={`Delete ${asset.name}`}
              >
                <Trash2 size={16} />
              </button>
            </div>
          </div>
        ))}
      </div>
    </ModalCard>
  )
}
