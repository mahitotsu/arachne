import { createTheme } from '@mui/material/styles';

export const operatorConsoleTheme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#245b8f',
      dark: '#163b60',
      light: '#7ba3c9',
    },
    secondary: {
      main: '#607d8b',
    },
    background: {
      default: '#f3f5f7',
      paper: '#ffffff',
    },
    success: {
      main: '#2e7d32',
    },
    warning: {
      main: '#b26a00',
    },
    error: {
      main: '#b3261e',
    },
  },
  shape: {
    borderRadius: 14,
  },
  typography: {
    fontFamily: "Roboto, 'Noto Sans JP', 'Segoe UI', sans-serif",
    h4: {
      fontWeight: 600,
      letterSpacing: '-0.02em',
    },
    h5: {
      fontWeight: 600,
    },
    h6: {
      fontWeight: 600,
    },
    subtitle2: {
      fontWeight: 600,
      letterSpacing: '0.03em',
    },
  },
  components: {
    MuiPaper: {
      styleOverrides: {
        root: {
          boxShadow: '0 12px 32px rgba(15, 23, 42, 0.06)',
        },
      },
    },
  },
});
