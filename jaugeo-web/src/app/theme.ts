import { createTheme } from '@mui/material/styles'
import { frFR } from '@mui/material/locale'
import { frFR as grilleFrFR } from '@mui/x-data-grid/locales'
import type {} from '@mui/x-data-grid/themeAugmentation'

export const theme = createTheme(
  {
    palette: {
      primary: { main: '#0b4f6c', contrastText: '#ffffff' },
      secondary: { main: '#f5a623', contrastText: '#1d1d1d' },
      background: { default: '#f4f6f8', paper: '#ffffff' },
      success: { main: '#2e7d32' },
      warning: { main: '#ed6c02' },
    },
    shape: { borderRadius: 8 },
    typography: {
      fontFamily: 'Roboto, "Segoe UI", Arial, sans-serif',
      h4: { fontWeight: 600 },
      h5: { fontWeight: 600 },
      h6: { fontWeight: 600 },
      button: { textTransform: 'none', fontWeight: 500 },
    },
    components: {
      MuiButton: { defaultProps: { disableElevation: true } },
      MuiPaper: { defaultProps: { elevation: 0 }, styleOverrides: { root: { border: '1px solid #e3e7eb' } } },
      MuiAppBar: { styleOverrides: { root: { border: 'none' } } },
      MuiDrawer: { styleOverrides: { paper: { border: 'none' } } },
      MuiTextField: { defaultProps: { size: 'small', fullWidth: true } },
      MuiDataGrid: {
        styleOverrides: {
          root: { backgroundColor: '#ffffff', border: '1px solid #e3e7eb' },
          columnHeaders: { fontWeight: 600 },
        },
      },
    },
  },
  frFR,
  grilleFrFR,
)
