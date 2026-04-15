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

import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MemoryRouter } from 'react-router-dom'

import { NotFoundPage } from './NotFoundPage'

describe('NotFoundPage', () => {
  it('invokes create callback', () => {
    let clicked = 0
    render(<MemoryRouter><NotFoundPage path="docs/missing" onCreate={() => { clicked += 1 }} /></MemoryRouter>)
    fireEvent.click(screen.getByRole('button', { name: /Create page by path/i }))
    expect(clicked).toBe(1)
  })
})
