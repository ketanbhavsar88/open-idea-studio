import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type { Artifact, UpdateArtifactRequest } from './types'

export function useArtifacts(projectId: string) {
  return useQuery({ queryKey: ['artifacts', projectId], queryFn: () => api.listArtifacts(projectId) })
}

/** Invalidates everything a content change can affect. */
export function useInvalidateProject(projectId: string) {
  const queryClient = useQueryClient()
  return () => {
    for (const key of ['artifacts', 'revisions', 'project', 'messages', 'flows']) {
      queryClient.invalidateQueries({ queryKey: [key, projectId] })
    }
    queryClient.invalidateQueries({ queryKey: ['projects'] })
  }
}

export function useUpdateArtifact(projectId: string) {
  const invalidate = useInvalidateProject(projectId)
  return useMutation({
    mutationFn: ({ artifact, changes }: { artifact: Artifact; changes: Omit<UpdateArtifactRequest, 'version'> }) =>
      api.updateArtifact(projectId, artifact.key, { ...changes, version: artifact.version }),
    onSettled: invalidate,
  })
}
