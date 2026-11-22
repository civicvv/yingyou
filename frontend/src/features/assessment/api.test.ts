import { describe, expect, it } from 'vitest'
import { qualitySignalLabel, vocabBandLabel } from './api'

describe('assessment labels', () => {
  it('maps vocabulary bands to recommendation text', () => {
    expect(vocabBandLabel.V1).toBe('高频词汇起步')
    expect(vocabBandLabel.V4).toBe('尝试高级表达')
  })

  it('explains quality signals', () => {
    expect(qualitySignalLabel('AT_CEILING')).toContain('覆盖上限')
    expect(qualitySignalLabel('UNSTABLE')).toContain('波动')
    expect(qualitySignalLabel('STANDARD')).toContain('稳定')
  })
})
