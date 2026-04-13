import { withAppBasePath } from '../../lib/basePath'

export function buildDynamicApiRunPath(spaceSlug: string, apiSlug: string): string {
  const safeSpaceSlug = encodeURIComponent(spaceSlug.trim())
  const safeApiSlug = encodeURIComponent(apiSlug.trim())
  return `/api/spaces/${safeSpaceSlug}/dynamic-apis/${safeApiSlug}/run`
}

export function buildBrowserDynamicApiRunPath(spaceSlug: string, apiSlug: string, basePath?: string): string {
  const runPath = buildDynamicApiRunPath(spaceSlug, apiSlug)
  return basePath === undefined ? withAppBasePath(runPath) : withAppBasePath(runPath, basePath)
}
