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
  fullTextIndexedDocuments?: number
  embeddingDocuments?: number
  embeddingIndexedDocuments?: number
  staleDocuments?: number
  embeddingsReady?: boolean
  lastIndexingError?: string | null
  embeddingModelId?: string | null
  lastFullRebuildAt?: string | null
  lastUpdatedAt: string
}

export interface WikiSemanticSearchResult {
  mode: string
  semanticReady: boolean
  fallbackReason?: string | null
  semanticHits: WikiSearchHit[]
  fallbackHits: WikiSearchHit[]
}

export interface SpaceChatSource {
  path: string
  title: string
  excerpt: string
}

export interface SpaceChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface SpaceChatRequest {
  message: string
  history: SpaceChatMessage[]
  modelConfigId: string | null
  summary: string | null
  turnCount: number | null
}

export interface SpaceChatResponse {
  answer: string
  modelConfigId: string
  summary: string | null
  compacted: boolean
  sources: SpaceChatSource[]
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
  imageVersion: string
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

export interface Space {
  id: string
  slug: string
  name: string
  createdAt: string
}

export interface ApiKey {
  id: string
  name: string
  subject: string
  spaceId: string | null
  roles: UserRole[]
  createdAt: string
  expiresAt: string | null
  revoked: boolean
}

export interface IssuedApiKey {
  apiKey: ApiKey
  token: string
}

export interface CreateApiKeyPayload {
  name: string
  roles?: UserRole[]
  expiresAt?: string | null
}

export interface Secret {
  value: string | null
  encrypted: boolean
  present: boolean
}

export type LlmApiType = 'openai' | 'anthropic' | 'gemini'
export type LlmModelKind = 'chat' | 'embedding'

export interface LlmProviderConfig {
  apiKey: Secret | null
  baseUrl: string | null
  requestTimeoutSeconds: number | null
  apiType: LlmApiType | null
  legacyApi: boolean | null
  createdAt?: string
  updatedAt?: string
}

export interface LlmModelConfig {
  id: string
  provider: string
  modelId: string
  displayName: string | null
  kind: LlmModelKind
  enabled: boolean
  supportsTemperature: boolean | null
  maxInputTokens: number | null
  dimensions: number | null
  temperature: number | null
  reasoningEffort: string | null
  createdAt?: string
  updatedAt?: string
}

export interface ModelRegistryConfig {
  repositoryUrl: string | null
  branch: string | null
}

export interface ModelReasoningLevel {
  maxInputTokens: number | null
}

export interface ModelReasoningProfile {
  default: string | null
  levels: Record<string, ModelReasoningLevel>
}

export interface ModelCatalogEntry {
  provider: string | null
  displayName: string | null
  supportsVision: boolean | null
  supportsTemperature: boolean | null
  maxInputTokens: number | null
  reasoning: ModelReasoningProfile | null
}

export interface ModelRegistryResolveResult {
  defaultSettings: ModelCatalogEntry | null
  configSource: string | null
  cacheStatus: string
}

export interface LlmSettings {
  providers: Record<string, LlmProviderConfig>
  models: LlmModelConfig[]
  modelRegistry: ModelRegistryConfig | null
}

export interface SaveLlmProviderPayload {
  name?: string
  apiKey?: Secret | null
  baseUrl?: string | null
  requestTimeoutSeconds?: number | null
  apiType?: LlmApiType | null
  legacyApi?: boolean | null
}

export interface SaveLlmModelPayload {
  provider: string
  modelId: string
  displayName?: string | null
  kind?: LlmModelKind | null
  enabled?: boolean | null
  supportsTemperature?: boolean | null
  maxInputTokens?: number | null
  dimensions?: number | null
  temperature?: number | null
  reasoningEffort?: string | null
}

export interface LlmProviderCheckResult {
  success: boolean
  message: string
  statusCode: number | null
  models?: string[] | null
}

export interface DynamicSpaceApiConfig {
  id: string
  slug: string
  name: string
  description: string | null
  modelConfigId: string
  systemPrompt: string
  enabled: boolean
  maxIterations: number
  createdAt: string
  updatedAt: string
}

export interface SaveDynamicSpaceApiPayload {
  slug: string
  name?: string | null
  description?: string | null
  modelConfigId: string
  systemPrompt: string
  enabled?: boolean | null
  maxIterations?: number | null
}

export interface DynamicSpaceApiRunResult {
  apiId: string
  apiSlug: string
  result: unknown
  rawResponse: string
  iterations: number
  toolCallCount: number
}
