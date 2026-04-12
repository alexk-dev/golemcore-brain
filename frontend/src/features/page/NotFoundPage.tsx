import { ErrorPage } from './ErrorPage'

interface NotFoundPageProps {
  path: string
  onCreate: () => void
  canCreate?: boolean
  signInHref?: string | null
}

export function NotFoundPage({ path, onCreate, canCreate = true, signInHref = null }: NotFoundPageProps) {
  return (
    <ErrorPage
      variant="not-found"
      path={path}
      canCreate={canCreate}
      signInHref={signInHref}
      onCreate={onCreate}
    />
  )
}
