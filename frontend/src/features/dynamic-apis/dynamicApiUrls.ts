export function buildDynamicApiRunPath(spaceSlug: string, apiSlug: string): string {
  const safeSpaceSlug = encodeURIComponent(spaceSlug.trim())
  const safeApiSlug = encodeURIComponent(apiSlug.trim())
  return `/api/spaces/${safeSpaceSlug}/dynamic-apis/${safeApiSlug}/run`
}
