import { ModalCard } from './ModalCard'

interface DeleteDialogProps {
  open: boolean
  path: string
  onOpenChange: (open: boolean) => void
  onConfirm: () => Promise<void>
}

export function DeleteDialog({
  open,
  path,
  onOpenChange,
  onConfirm,
}: DeleteDialogProps) {
  return (
    <ModalCard
      open={open}
      title="Delete page"
      description={`This permanently deletes /${path || ''} from the markdown workspace.`}
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
        The underlying markdown file or section folder will be removed.
      </p>
    </ModalCard>
  )
}
