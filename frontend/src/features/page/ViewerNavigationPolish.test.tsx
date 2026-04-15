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

import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import App from '../../App'
import { useEditorStore } from '../../stores/editor'
import { useTreeStore } from '../../stores/tree'
import { useUiStore } from '../../stores/ui'
import { useViewerStore } from '../../stores/viewer'

vi.mock('../../lib/api', () => ({
  getAuthConfig: vi.fn(async () => ({
    authDisabled: true,
    publicAccess: true,
    user: null,
  })),
  getConfig: vi.fn(async () => ({
    publicAccess: true,
    hideLinkMetadataSection: false,
    authDisabled: true,
    maxAssetUploadSizeBytes: 1024,
    siteTitle: 'GolemCore Brain',
    rootPath: '',
    imageVersion: 'dev',
  })),
  getTree: vi.fn(async () => ({
    id: 'root',
    path: '',
    parentPath: null,
    title: 'Welcome',
    slug: '',
    kind: 'ROOT',
    hasChildren: true,
    children: [
      {
        id: 'guides',
        path: 'guides',
        parentPath: '',
        title: 'Guides',
        slug: 'guides',
        kind: 'SECTION',
        hasChildren: true,
        children: [
          {
            id: 'guides/setup',
            path: 'guides/setup',
            parentPath: 'guides',
            title: 'Setup',
            slug: 'setup',
            kind: 'PAGE',
            hasChildren: false,
            children: [],
          },
        ],
      },
    ],
  })),
  getPageByPath: vi.fn(async (path: string) => {
    if (path === 'guides') {
      return {
        id: 'guides',
        path: 'guides',
        parentPath: '',
        title: 'Guides',
        slug: 'guides',
        kind: 'SECTION',
        content: '# Guides\n\nSection landing page.',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
        children: [
          {
            id: 'guides/setup',
            path: 'guides/setup',
            parentPath: 'guides',
            title: 'Setup',
            slug: 'setup',
            kind: 'PAGE',
            hasChildren: false,
            children: [],
          },
        ],
      }
    }
    return {
      id: path || 'root',
      path,
      parentPath: '',
      title: path ? 'Setup' : 'Welcome',
      slug: path.split('/').pop() || '',
      kind: path ? 'PAGE' : 'ROOT',
      content: '# Demo\n\nLoaded page',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      children: [],
    }
  }),
  getPageHistory: vi.fn(async () => []),
  getPageHistoryVersion: vi.fn(),
  restorePageHistory: vi.fn(async () => ({
    id: 'guides',
    path: 'guides',
    parentPath: '',
    title: 'Guides',
    slug: 'guides',
    kind: 'SECTION',
    content: '# Guides\n\nSection landing page.',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    children: [],
  })),
  getLinkStatus: vi.fn(async () => ({
    backlinks: [],
    brokenIncoming: [],
    outgoings: [],
    brokenOutgoings: [],
  })),
  getSearchStatus: vi.fn(async () => ({
    mode: 'live-scan',
    ready: true,
    indexedDocuments: 1,
    lastUpdatedAt: '2026-01-01T00:00:00Z',
  })),
  logout: vi.fn(async () => ({ message: 'Logged out', user: null })),
  changePassword: vi.fn(async () => ({ message: 'Password changed', user: null })),
  createPage: vi.fn(),
  deletePage: vi.fn(),
  movePage: vi.fn(),
  copyPage: vi.fn(),
  convertPage: vi.fn(),
  sortSection: vi.fn(),
  ensurePage: vi.fn(),
  lookupPath: vi.fn(),
  updatePage: vi.fn(),
  listAssets: vi.fn(async () => []),
  uploadAsset: vi.fn(),
  renameAsset: vi.fn(),
  deleteAsset: vi.fn(),
  searchPages: vi.fn(async () => []),
  listUsers: vi.fn(async () => []),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  deleteUserAccount: vi.fn(),
  planMarkdownImport: vi.fn(async () => ({ items: [] })),
  applyMarkdownImport: vi.fn(async () => ({ importedCount: 0, createdCount: 0, updatedCount: 0, skippedCount: 0, items: [] })),
}))

describe('Viewer navigation polish', () => {
  beforeEach(() => {
    useTreeStore.setState({
      tree: null,
      loading: false,
      error: null,
      activeNodeId: null,
      openNodeIdSet: {},
      byPath: {},
      byId: {},
      flatPages: [],
      manualNodeStateById: {},
      mustOpenNodeIdSet: {},
      suggestedOpenNodeIdSet: {},
    })
    useViewerStore.setState({ page: null, linkStatus: null, history: [], loading: false, error: null })
    useEditorStore.setState({
      page: null,
      initialPage: null,
      title: '',
      slug: '',
      content: '',
      loading: false,
      error: null,
    })
    useUiStore.setState({
      isDark: false,
      sidebarVisible: true,
      searchOpen: false,
      quickSwitcherOpen: false,
      authDisabled: true,
      publicAccess: true,
      currentUser: null,
    })
  })

  it('shows breadcrumbs and empty-section guidance for section pages', async () => {
    render(
      <MemoryRouter initialEntries={['/guides']}>
        <App />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(screen.getByRole('link', { name: 'Home' })).toBeInTheDocument()
    })

    expect(screen.getByRole('link', { name: 'Guides' })).toBeInTheDocument()
    expect(screen.getByText('Section contents')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Setup' })).toHaveAttribute('href', '/guides/setup')
  })
})
