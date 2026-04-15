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
