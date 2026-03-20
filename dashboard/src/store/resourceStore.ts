import { create } from 'zustand'
import type { ResourceInfo } from '@/types/resources'

interface ResourceStore {
  resources:   ResourceInfo[]
  lastUpdated: Date | null
  setResources: (resources: ResourceInfo[]) => void
}

export const useResourceStore = create<ResourceStore>(set => ({
  resources:   [],
  lastUpdated: null,
  setResources: resources => set({ resources, lastUpdated: new Date() })
}))
