import { inject, provide, type InjectionKey } from 'vue'
import { createPrototypeState, type PrototypeState } from './state.ts'

const KEY: InjectionKey<PrototypeState> = Symbol('designer-prototype')

export function providePrototype() {
  const state = createPrototypeState()
  provide(KEY, state)
  return state
}

export function usePrototype(): PrototypeState {
  const state = inject(KEY)
  if (!state) throw new Error('原型组件必须在 ProtoDesigner 之下使用')
  return state
}
