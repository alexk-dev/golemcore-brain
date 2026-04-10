import { Link2, Link2Off } from 'lucide-react'
import { toast } from 'sonner'

import { restorePageHistory } from '../../lib/api'
import { useUiStore } from '../../stores/ui'
import { useViewerStore } from '../../stores/viewer'

export function LinkInfo() {
  const page = useViewerStore((state) => state.page)
  const linkStatus = useViewerStore((state) => state.linkStatus)
  const history = useViewerStore((state) => state.history)
  const currentUser = useUiStore((state) => state.currentUser)
  const authDisabled = useUiStore((state) => state.authDisabled)
  const canRestore = authDisabled || currentUser?.role === 'ADMIN' || currentUser?.role === 'EDITOR'

  const handleRestore = async (versionId: string) => {
    if (!page) {
      return
    }
    try {
      await restorePageHistory(page.path, versionId)
      toast.success('Version restored')
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

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
                  <a href={`/${item.fromPath}`}>{item.fromTitle}</a>
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
                  <a href={`/${item.toPath}`}>{item.toTitle}</a>
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
                  <span>{entry.title}</span>
                  {canRestore ? (
                    <button type="button" className="action-button-secondary" onClick={() => void handleRestore(entry.id)}>
                      Restore {entry.title}
                    </button>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  )
}
