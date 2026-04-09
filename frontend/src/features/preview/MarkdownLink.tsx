import type { AnchorHTMLAttributes, ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { normalizeWikiPath, resolveWikiLinkPath } from '../../lib/paths'

interface MarkdownLinkProps extends AnchorHTMLAttributes<HTMLAnchorElement> {
  href?: string
  children?: ReactNode
  currentPath?: string
}

export function MarkdownLink({ href, children, currentPath, ...props }: MarkdownLinkProps) {
  if (!href) {
    return <>{children}</>
  }

  const isInternal =
    !href.startsWith('http') &&
    !href.startsWith('mailto:') &&
    !href.startsWith('#') &&
    !href.startsWith('/api/assets')

  if (isInternal) {
    const nextPath = href.startsWith('/')
      ? normalizeWikiPath(href)
      : resolveWikiLinkPath(currentPath ?? '', href)
    return (
      <Link to={`/${nextPath}`} {...props} className="text-accent hover:underline">
        {children}
      </Link>
    )
  }

  return (
    <a href={href} {...props} className="text-accent hover:underline" target="_blank" rel="noreferrer">
      {children}
    </a>
  )
}
