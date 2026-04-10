export type WikiNodeKind = 'ROOT' | 'SECTION' | 'PAGE'
export type UserRole = 'ADMIN' | 'EDITOR' | 'VIEWER'
export type WikiImportAction = 'CREATE' | 'UPDATE' | 'SKIP'
export type WikiImportPolicy = 'OVERWRITE' | 'KEEP_EXISTING'

export interface PublicUserView {
  id: string
  username: string
  email: string
  role: UserRole
}

export interface AuthConfig {
  authDisabled: boolean
  publicAccess: boolean
  user: PublicUserView | null
}

export interface UpdateUserPayload {
  username: string
  email: string
  password?: string
  role: UserRole
}

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
  revision?: string
  children: WikiTreeNode[]
}

export interface WikiPageHistoryEntry {
  id: string
  title: string
  slug: string
  recordedAt: string
  author?: string
  reason?: string
  summary?: string
}

export interface WikiPageHistoryVersion {
  id: string
  title: string
  slug: string
  content: string
  recordedAt: string
  author?: string
  reason?: string
  summary?: string
}

export interface WikiSearchHit {
  id: string
  path: string
  title: string
  excerpt: string
  parentPath: string | null
  kind: WikiNodeKind
}

export interface WikiSearchStatus {
  mode: string
  ready: boolean
  indexedDocuments: number
  lastUpdatedAt: string
}

export interface WikiImportItem {
  path: string
  title: string
  kind: WikiNodeKind
  action: WikiImportAction
  policy: WikiImportPolicy
  implicitSection: boolean
  existing: boolean
  selected: boolean
  sourcePath: string
  note?: string
}

export interface WikiImportPlanResponse {
  targetRootPath: string
  createCount: number
  updateCount: number
  skipCount: number
  warnings: string[]
  items: WikiImportItem[]
}

export interface WikiImportApplyResponse {
  importedCount: number
  createdCount: number
  updatedCount: number
  skippedCount: number
  importedRootPath: string
  warnings: string[]
  items: WikiImportItem[]
}

export interface MarkdownImportOptions {
  targetRootPath?: string
  items?: Array<{
    sourcePath: string
    selected: boolean
    policy: WikiImportPolicy
  }>
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
  revision?: string
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

export interface ConvertPagePayload {
  targetKind: Exclude<WikiNodeKind, 'ROOT'>
}

export interface AssetDialogState {
  open: boolean
  pagePath: string
}
