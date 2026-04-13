import type { AnchorHTMLAttributes, ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { isAppApiPath } from '../../lib/basePath'
import { normalizeWikiPath, resolveWikiLinkPath } from '../../lib/paths'
import { normalizeAssetUrl, toBrowserAssetUrl } from '../assets/assetUrls'

interface MarkdownLinkProps extends AnchorHTMLAttributes<HTMLAnchorElement> {
  href?: string
  children?: ReactNode
  currentPath?: string
}

export function MarkdownLink({ href, children, currentPath, ...props }: MarkdownLinkProps) {
  if (!href) {
    return <>{children}</>
  }

  const normalizedHref = normalizeAssetUrl(href)
  const isInternal =
    !normalizedHref.startsWith('http') &&
    !normalizedHref.startsWith('mailto:') &&
    !normalizedHref.startsWith('#') &&
    !isAppApiPath(normalizedHref)

  if (isInternal) {
    const nextPath = normalizedHref.startsWith('/')
      ? normalizeWikiPath(normalizedHref)
      : resolveWikiLinkPath(currentPath ?? '', normalizedHref)
    return (
      <Link to={`/${nextPath}`} {...props} className="text-accent hover:underline">
        {children}
      </Link>
    )
  }

  return (
    <a href={toBrowserAssetUrl(normalizedHref)} {...props} className="text-accent hover:underline" target="_blank" rel="noreferrer">
      {children}
    </a>
  )
}
