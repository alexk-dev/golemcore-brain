import copy from 'copy-to-clipboard'
import { Copy } from 'lucide-react'
import { toast } from 'sonner'

import { buildBrowserDynamicApiRunPath } from './dynamicApiUrls'

interface DynamicApiEndpointPreviewProps {
  spaceSlug: string
  apiSlug: string
  requestJson?: string
  showCurl?: boolean
}

function buildCurlCommand(endpointPath: string, requestJson: string): string {
  return [
    `curl -X POST "${endpointPath}"`,
    '  -H "Authorization: Bearer <SPACE_API_KEY>"',
    '  -H "Content-Type: application/json"',
    `  -d '${requestJson}'`,
  ].join(' \\\n')
}

function copyText(value: string, successMessage: string): void {
  if (!copy(value)) {
    toast.error('Could not copy to clipboard')
    return
  }
  toast.success(successMessage)
}

export function DynamicApiEndpointPreview({
  spaceSlug,
  apiSlug,
  requestJson = '{"query":"What should I know from this space?"}',
  showCurl = false,
}: DynamicApiEndpointPreviewProps) {
  const endpointPath = buildBrowserDynamicApiRunPath(spaceSlug, apiSlug)
  const curlCommand = buildCurlCommand(endpointPath, requestJson)

  return (
    <div className="rounded-lg border border-surface-border bg-surface-alt/60 px-3 py-2 text-xs md:col-span-2">
      <div className="mb-1 flex flex-wrap items-center justify-between gap-2">
        <span className="font-medium text-foreground">Endpoint</span>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="action-button-secondary px-2 py-1 text-xs"
            onClick={() => copyText(endpointPath, 'Endpoint URL copied')}
          >
            <Copy size={13} />
            Copy URL
          </button>
          {showCurl ? (
            <button
              type="button"
              className="action-button-secondary px-2 py-1 text-xs"
              onClick={() => copyText(curlCommand, 'cURL copied')}
            >
              <Copy size={13} />
              Copy cURL
            </button>
          ) : null}
        </div>
      </div>
      <code className="block overflow-auto whitespace-nowrap rounded bg-background px-2 py-1 text-muted">
        POST {endpointPath}
      </code>
      <div className="mt-2 text-muted">Use a space-scoped API key with the Authorization bearer header.</div>
      {showCurl ? (
        <pre className="mt-2 max-h-40 overflow-auto rounded bg-background px-2 py-2 text-muted">{curlCommand}</pre>
      ) : null}
    </div>
  )
}
