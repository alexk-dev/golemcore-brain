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

import { DynamicSpaceApisPage } from './DynamicSpaceApisPage'
import { useSpaceStore } from '../../stores/space'
import { useUiStore } from '../../stores/ui'

const listDynamicSpaceApisMock = vi.fn()
const getLlmSettingsMock = vi.fn()
const runDynamicSpaceApiMock = vi.fn()
const createDynamicSpaceApiMock = vi.fn()
const updateDynamicSpaceApiMock = vi.fn()
const deleteDynamicSpaceApiMock = vi.fn()

vi.mock('../../lib/api', () => ({
  listDynamicSpaceApis: (...args: unknown[]) => listDynamicSpaceApisMock(...args),
  getLlmSettings: (...args: unknown[]) => getLlmSettingsMock(...args),
  runDynamicSpaceApi: (...args: unknown[]) => runDynamicSpaceApiMock(...args),
  createDynamicSpaceApi: (...args: unknown[]) => createDynamicSpaceApiMock(...args),
  updateDynamicSpaceApi: (...args: unknown[]) => updateDynamicSpaceApiMock(...args),
  deleteDynamicSpaceApi: (...args: unknown[]) => deleteDynamicSpaceApiMock(...args),
}))

vi.mock('../../lib/basePath', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/basePath')>()
  return {
    ...actual,
    appBasePath: '/brain',
    withAppBasePath: (path: string, basePath = '/brain') => actual.withAppBasePath(path, basePath),
  }
})

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

describe('DynamicSpaceApisPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUiStore.setState({
      authDisabled: false,
      currentUser: {
        id: 'admin-1',
        username: 'admin',
        email: 'admin@example.com',
        role: 'ADMIN',
      },
    })
    useSpaceStore.setState({
      spaces: [
        {
          id: 'space-1',
          slug: 'docs',
          name: 'Docs',
          createdAt: '2026-01-01T00:00:00Z',
        },
      ],
      activeSlug: 'docs',
      loaded: true,
    })
    listDynamicSpaceApisMock.mockResolvedValue([
      {
        id: 'api-1',
        slug: 'knowledge-search',
        name: 'Knowledge Search',
        description: 'Answers questions',
        modelConfigId: 'chat-model',
        systemPrompt: 'Answer as JSON',
        enabled: true,
        maxIterations: 6,
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ])
    getLlmSettingsMock.mockResolvedValue({
      providers: {},
      models: [
        {
          id: 'chat-model',
          provider: 'openai',
          modelId: 'gpt-test',
          displayName: 'GPT Test',
          kind: 'chat',
          enabled: true,
          maxInputTokens: null,
          dimensions: null,
          temperature: null,
        },
      ],
    })
  })

  it('shows the full browser-visible run endpoint for each dynamic API', async () => {
    render(
      <MemoryRouter>
        <DynamicSpaceApisPage />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(screen.getAllByText('/brain/api/spaces/docs/dynamic-apis/knowledge-search/run').length).toBeGreaterThan(0)
    })
  })
})
