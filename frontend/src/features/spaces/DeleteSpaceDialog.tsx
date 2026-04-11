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
