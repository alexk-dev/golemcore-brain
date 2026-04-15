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

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { ImportPage } from './ImportPage'
import { useTreeStore } from '../../stores/tree'

const planMarkdownImportMock = vi.fn()
const applyMarkdownImportMock = vi.fn()
const navigateMock = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => navigateMock,
  }
})

vi.mock('../../lib/api', () => ({
  planMarkdownImport: (...args: unknown[]) => planMarkdownImportMock(...args),
  applyMarkdownImport: (...args: unknown[]) => applyMarkdownImportMock(...args),
}))

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

describe('ImportPage', () => {
  it('previews and applies a markdown zip import', async () => {
    useTreeStore.setState({
      reloadTree: vi.fn(async () => undefined),
    })
    planMarkdownImportMock.mockResolvedValue({
      targetRootPath: 'knowledge',
      createCount: 2,
      updateCount: 0,
      skipCount: 0,
      warnings: [],
      items: [
        { path: 'knowledge/guides', title: 'Guides', kind: 'SECTION', action: 'CREATE', policy: 'OVERWRITE', implicitSection: false, existing: false, selected: true, sourcePath: 'guides/index.md', note: 'Create' },
        { path: 'knowledge/guides/setup', title: 'Setup', kind: 'PAGE', action: 'CREATE', policy: 'OVERWRITE', implicitSection: false, existing: false, selected: true, sourcePath: 'guides/setup.md', note: 'Create' },
      ],
    })
    applyMarkdownImportMock.mockResolvedValue({
      importedCount: 2,
      createdCount: 2,
      updatedCount: 0,
      skippedCount: 0,
      importedRootPath: 'knowledge/guides',
      warnings: [],
      items: [],
    })

    render(
      <MemoryRouter>
        <ImportPage />
      </MemoryRouter>,
    )

    const file = new File(['zip-bytes'], 'docs.zip', { type: 'application/zip' })
    fireEvent.change(screen.getByLabelText(/Archive file/i), {
      target: { files: [file] },
    })
    fireEvent.change(screen.getByLabelText(/Target root path/i), {
      target: { value: 'knowledge' },
    })

    fireEvent.click(screen.getByRole('button', { name: /Preview import/i }))

    await waitFor(() => {
      expect(screen.getByText('knowledge/guides/setup')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: /Apply import/i }))

    await waitFor(() => {
      expect(applyMarkdownImportMock).toHaveBeenCalledWith(file, {
        targetRootPath: 'knowledge',
        items: [
          { sourcePath: 'guides/index.md', selected: true, policy: 'OVERWRITE' },
          { sourcePath: 'guides/setup.md', selected: true, policy: 'OVERWRITE' },
        ],
      })
    })
    expect(screen.getByText(/Imported 2 item/i)).toBeInTheDocument()
    expect(navigateMock).toHaveBeenCalledWith('/knowledge/guides')
  })
})
