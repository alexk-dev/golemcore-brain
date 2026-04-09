import * as Dialog from '@radix-ui/react-dialog'
import { X } from 'lucide-react'
import type { PropsWithChildren, ReactNode } from 'react'

interface ModalCardProps extends PropsWithChildren {
  open: boolean
  title: string
  description?: string
  onOpenChange: (open: boolean) => void
  footer?: ReactNode
}

export function ModalCard({
  open,
  title,
  description,
  onOpenChange,
  children,
  footer,
}: ModalCardProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-slate-950/55 backdrop-blur-sm" />
        <Dialog.Content className="fixed top-1/2 left-1/2 z-50 w-[calc(100vw-2rem)] max-w-xl -translate-x-1/2 -translate-y-1/2 rounded-[28px] border border-surface-border bg-surface p-6 shadow-2xl outline-none">
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
            <Dialog.Close className="rounded-full border border-surface-border p-2 text-muted transition hover:bg-surface-alt hover:text-foreground">
              <X size={16} />
            </Dialog.Close>
          </div>
          <div className="space-y-4">{children}</div>
          {footer ? <div className="mt-6 flex justify-end gap-3">{footer}</div> : null}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
