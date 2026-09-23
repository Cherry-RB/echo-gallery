import type { ExperimentThemeColor } from '../types/experiment'

export const experimentThemeOptions: Array<{ value: ExperimentThemeColor; label: string; color: string; vividColor: string }> = [
  { value: 'LEAF', label: '葉綠', color: '#4f8f5b', vividColor: '#42ad63' },
  { value: 'LAKE', label: '湖藍', color: '#3e7fa3', vividColor: '#4b9bdd' },
  { value: 'AMBER', label: '琥珀', color: '#b7791f', vividColor: '#e6a23c' },
  { value: 'LAVENDER', label: '薰衣草', color: '#7b61a8', vividColor: '#9b7de0' },
  { value: 'CORAL', label: '珊瑚', color: '#b85c4c', vividColor: '#e77b6b' },
  { value: 'MIST', label: '霧灰', color: '#66788a', vividColor: '#7696b5' },
]

export const getExperimentThemeStyle = (themeColor: ExperimentThemeColor) => {
  const theme = experimentThemeOptions.find(option => option.value === themeColor) ?? experimentThemeOptions[0]
  return {
    '--experiment-accent': theme.color,
    '--experiment-vivid': theme.vividColor,
  }
}
