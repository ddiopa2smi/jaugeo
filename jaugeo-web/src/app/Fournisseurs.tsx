import CssBaseline from '@mui/material/CssBaseline'
import { ThemeProvider } from '@mui/material/styles'
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { ZoneConfirmation, ZoneNotification } from '../shared/composants'
import { creerQueryClient } from './queryClient'
import { theme } from './theme'

export function Fournisseurs({ children, client }: { children: ReactNode; client?: QueryClient }) {
  const [queryClient] = useState(() => client ?? creerQueryClient())
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <QueryClientProvider client={queryClient}>
        {children}
        <ZoneNotification />
        <ZoneConfirmation />
      </QueryClientProvider>
    </ThemeProvider>
  )
}
