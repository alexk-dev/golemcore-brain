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

import * as Dialog from '@radix-ui/react-dialog'
import clsx from 'clsx'
import { X } from 'lucide-react'
import type { PropsWithChildren, ReactNode } from 'react'

interface ModalCardProps extends PropsWithChildren {
  open: boolean
  title: string
  description?: string
  onOpenChange: (open: boolean) => void
  footer?: ReactNode
  /** Widens the card for side-by-side content such as a conflict diff. */
  wide?: boolean
}

export function ModalCard({
  open,
  title,
  description,
  onOpenChange,
  children,
  footer,
  wide = false,
}: ModalCardProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-slate-950/55 backdrop-blur-sm" />
        <Dialog.Content
          className={clsx(
            'fixed top-1/2 left-1/2 z-50 max-h-[calc(100dvh-2rem)] w-[calc(100vw-2rem)] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-[28px] border border-surface-border bg-surface p-6 shadow-2xl outline-none',
            wide ? 'max-w-5xl' : 'max-w-xl',
          )}
        >
          <div className="mb-5 flex items-start justify-between gap-4">
            <div>
              <Dialog.Title className="text-xl font-semibold text-foreground">
                {title}
              </Dialog.Title>
              {description ? (
                <Dialog.Description className="mt-1 text-sm text-muted">
                  {description}
                </Dialog.Description>
              ) : null}
            </div>
            <Dialog.Close aria-label="Close dialog" className="inline-flex min-h-11 min-w-11 shrink-0 items-center justify-center rounded-full border border-surface-border p-2 text-muted transition hover:bg-surface-alt hover:text-foreground md:min-h-0 md:min-w-0">
              <X size={16} />
            </Dialog.Close>
          </div>
          <div className="space-y-4">{children}</div>
          {footer ? <div className="mt-6 flex flex-wrap justify-end gap-3">{footer}</div> : null}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
