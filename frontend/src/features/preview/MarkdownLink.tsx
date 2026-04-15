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
