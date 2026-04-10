import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { uploadAsset } from '../../lib/api'
import { AssetManagerDialog } from './AssetManagerDialog'

vi.mock('../../lib/api', () => ({
  listAssets: vi.fn(async () => [
    {
      name: 'image.png',
      path: '/api/assets?path=docs/page&name=image.png',
      size: 1024,
      contentType: 'image/png',
    },
    {
      name: 'audio.mp3',
      path: '/api/assets?path=docs/page&name=audio.mp3',
      size: 2048,
      contentType: 'audio/mpeg',
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
  beforeEach(() => {
    vi.mocked(uploadAsset).mockClear()
  })

  it('offers richer insertion modes and preview links for assets', async () => {
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

    fireEvent.click(screen.getByRole('button', { name: 'Insert image.png as image' }))
    fireEvent.click(screen.getByRole('button', { name: 'Insert image.png as link' }))
    fireEvent.click(screen.getByRole('button', { name: 'Insert audio.mp3 as media' }))
    fireEvent.click(screen.getByRole('button', { name: 'Insert audio.mp3 as link' }))

    await waitFor(() => {
      expect(inserted).toEqual([
        '![image.png](/api/assets?path=docs/page&name=image.png)',
        '[image.png](/api/assets?path=docs/page&name=image.png)',
        '<audio controls src="/api/assets?path=docs/page&name=audio.mp3"></audio>',
        '[audio.mp3](/api/assets?path=docs/page&name=audio.mp3)',
      ])
    })

    expect(screen.getByRole('link', { name: 'Preview image.png' })).toHaveAttribute('href', '/api/assets?path=docs/page&name=image.png')
    expect(screen.getByRole('link', { name: 'Preview audio.mp3' })).toHaveAttribute('href', '/api/assets?path=docs/page&name=audio.mp3')
  })

  it('notifies the editor when an asset is renamed', async () => {
    const renamed: string[] = []
    const changed = vi.fn()

    render(
      <AssetManagerDialog
        open={true}
        pagePath="docs/page"
        onOpenChange={() => undefined}
        onInsertMarkdown={() => undefined}
        onAssetRenamed={(oldAsset, newAsset) => renamed.push(`${oldAsset.name}->${newAsset.name}`)}
        onAssetsChanged={changed}
      />,
    )

    await screen.findByText('image.png')

    fireEvent.click(screen.getByRole('button', { name: 'Rename image.png' }))
    fireEvent.change(screen.getByDisplayValue('image.png'), { target: { value: 'renamed.png' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(renamed).toEqual(['image.png->renamed.png'])
      expect(changed).toHaveBeenCalledTimes(1)
    })
  })

  it('uploads multiple files from the picker with per-file status', async () => {
    const changed = vi.fn()
    render(
      <AssetManagerDialog
        open={true}
        pagePath="docs/page"
        onOpenChange={() => undefined}
        onInsertMarkdown={() => undefined}
        onAssetsChanged={changed}
      />,
    )

    await screen.findByText('image.png')

    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    fireEvent.change(input, {
      target: {
        files: [
          new File(['one'], 'one.png', { type: 'image/png' }),
          new File(['two'], 'two.png', { type: 'image/png' }),
        ],
      },
    })

    await waitFor(() => {
      expect(uploadAsset).toHaveBeenCalledTimes(2)
      expect(changed).toHaveBeenCalledTimes(1)
    })
    expect(screen.getByText('one.png')).toBeInTheDocument()
    expect(screen.getByText('two.png')).toBeInTheDocument()
  })
})
