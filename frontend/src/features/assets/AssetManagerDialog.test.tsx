import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { AssetManagerDialog } from './AssetManagerDialog'

vi.mock('../../lib/api', () => ({
  listAssets: vi.fn(async () => [
    {
      name: 'image.png',
      path: '/api/assets?path=docs/page&name=image.png',
      size: 1024,
      contentType: 'image/png',
    },
  ]),
  uploadAsset: vi.fn(async () => undefined),
  renameAsset: vi.fn(async () => ({
    name: 'renamed.png',
    path: '/api/assets?path=docs/page&name=renamed.png',
    size: 1024,
    contentType: 'image/png',
  })),
  deleteAsset: vi.fn(async () => undefined),
}))

describe('AssetManagerDialog', () => {
  it('loads assets and inserts markdown for an image asset', async () => {
    const inserted: string[] = []
    render(
      <AssetManagerDialog
        open={true}
        pagePath="docs/page"
        onOpenChange={() => undefined}
        onInsertMarkdown={(markdown) => inserted.push(markdown)}
      />,
    )

    await screen.findByText('image.png')
    fireEvent.click(screen.getByRole('button', { name: /Insert/i }))

    await waitFor(() => {
      expect(inserted).toEqual(['![image.png](/api/assets?path=docs/page&name=image.png)'])
    })
  })
})
