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

import { ModalCard } from '../../components/ModalCard'

interface DeleteSpaceDialogProps {
  open: boolean
  slug: string
  onOpenChange: (open: boolean) => void
  onConfirm: () => Promise<void>
}

export function DeleteSpaceDialog({ open, slug, onOpenChange, onConfirm }: DeleteSpaceDialogProps) {
  return (
    <ModalCard
      open={open}
      title="Delete space"
      description={`Remove space "${slug}" from the wiki.`}
      onOpenChange={onOpenChange}
      footer={
        <>
          <button
            type="button"
            className="action-button-secondary"
            onClick={() => onOpenChange(false)}
          >
            Cancel
          </button>
          <button
            type="button"
            className="action-button-danger"
            onClick={() => {
              void onConfirm().then(() => onOpenChange(false))
            }}
          >
            Delete
          </button>
        </>
      }
    >
      <p className="muted-copy">
        The underlying files remain on disk but become inaccessible through the wiki.
      </p>
    </ModalCard>
  )
}
