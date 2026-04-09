export type WikiNodeKind = 'ROOT' | 'SECTION' | 'PAGE'

export interface WikiTreeNode {
  path: string
  parentPath: string | null
  title: string
  slug: string
  kind: WikiNodeKind
  hasChildren: boolean
  children: WikiTreeNode[]
}

export interface WikiPage {
  path: string
  parentPath: string | null
  title: string
  slug: string
  kind: WikiNodeKind
  content: string
  createdAt: string
  updatedAt: string
  children: WikiTreeNode[]
}

export interface WikiSearchHit {
  path: string
  title: string
  excerpt: string
  parentPath: string | null
  kind: WikiNodeKind
}

export interface WikiConfig {
  siteTitle: string
  rootPath: string
}

export interface CreatePagePayload {
  parentPath: string
  title: string
  slug?: string
  content: string
  kind: Exclude<WikiNodeKind, 'ROOT'>
}

export interface UpdatePagePayload {
  title: string
  slug?: string
  content: string
}

export interface MovePagePayload {
  targetParentPath: string
  targetSlug?: string
  beforeSlug?: string
}

export interface CopyPagePayload {
  targetParentPath: string
  targetSlug?: string
  beforeSlug?: string
}
