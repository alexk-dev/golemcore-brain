export type WikiNodeKind = 'ROOT' | 'SECTION' | 'PAGE'

export interface WikiTreeNode {
  id: string
  path: string
  parentPath: string | null
  title: string
  slug: string
  kind: WikiNodeKind
  hasChildren: boolean
  children: WikiTreeNode[]
}

export interface WikiPage {
  id: string
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
  id: string
  path: string
  title: string
  excerpt: string
  parentPath: string | null
  kind: WikiNodeKind
}

export interface WikiConfig {
  publicAccess: boolean
  hideLinkMetadataSection: boolean
  authDisabled: boolean
  maxAssetUploadSizeBytes: number
  siteTitle: string
  rootPath: string
}

export interface WikiPathLookupSegment {
  slug: string
  path: string
  exists: boolean
}

export interface WikiPathLookupResult {
  path: string
  exists: boolean
  segments: WikiPathLookupSegment[]
}

export interface WikiAsset {
  name: string
  path: string
  size: number
  contentType: string
}

export interface WikiLinkStatusItem {
  fromPageId: string | null
  fromPath: string | null
  fromTitle: string | null
  toPageId: string | null
  toPath: string | null
  toTitle: string | null
  broken: boolean
}

export interface WikiLinkStatus {
  backlinks: WikiLinkStatusItem[]
  brokenIncoming: WikiLinkStatusItem[]
  outgoings: WikiLinkStatusItem[]
  brokenOutgoings: WikiLinkStatusItem[]
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

export interface AssetDialogState {
  open: boolean
  pagePath: string
}
