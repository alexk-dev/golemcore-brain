import { Link2, Link2Off } from 'lucide-react'

import { useViewerStore } from '../../stores/viewer'

export function LinkInfo() {
  const linkStatus = useViewerStore((state) => state.linkStatus)

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
      </div>
    </div>
  )
}
