import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { ImportPage } from './ImportPage'

const planMarkdownImportMock = vi.fn()
const applyMarkdownImportMock = vi.fn()

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
    planMarkdownImportMock.mockResolvedValue({
      items: [
        { path: 'guides', title: 'Guides', kind: 'SECTION', action: 'CREATE', implicitSection: false, sourcePath: 'guides/index.md' },
        { path: 'guides/setup', title: 'Setup', kind: 'PAGE', action: 'CREATE', implicitSection: false, sourcePath: 'guides/setup.md' },
      ],
    })
    applyMarkdownImportMock.mockResolvedValue({
      importedCount: 2,
      createdCount: 2,
      updatedCount: 0,
      skippedCount: 0,
      items: [],
    })

    render(<ImportPage />)

    const file = new File(['zip-bytes'], 'docs.zip', { type: 'application/zip' })
    fireEvent.change(screen.getByLabelText(/Archive file/i), {
      target: { files: [file] },
    })

    fireEvent.click(screen.getByRole('button', { name: /Preview import/i }))

    await waitFor(() => {
      expect(screen.getByText('guides/setup')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: /Apply import/i }))

    await waitFor(() => {
      expect(applyMarkdownImportMock).toHaveBeenCalledWith(file)
    })
    expect(screen.getByText(/Imported 2 item/i)).toBeInTheDocument()
  })
})
