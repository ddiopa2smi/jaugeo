import AccountCircleIcon from '@mui/icons-material/AccountCircle'
import DashboardIcon from '@mui/icons-material/Dashboard'
import EventNoteIcon from '@mui/icons-material/EventNote'
import HistoryIcon from '@mui/icons-material/History'
import LocalGasStationIcon from '@mui/icons-material/LocalGasStation'
import LogoutIcon from '@mui/icons-material/Logout'
import MenuIcon from '@mui/icons-material/Menu'
import OilBarrelIcon from '@mui/icons-material/OilBarrel'
import PeopleIcon from '@mui/icons-material/People'
import SellIcon from '@mui/icons-material/Sell'
import AppBar from '@mui/material/AppBar'
import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import Divider from '@mui/material/Divider'
import Drawer from '@mui/material/Drawer'
import IconButton from '@mui/material/IconButton'
import List from '@mui/material/List'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import ListSubheader from '@mui/material/ListSubheader'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import Toolbar from '@mui/material/Toolbar'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import useMediaQuery from '@mui/material/useMediaQuery'
import { useTheme } from '@mui/material/styles'
import { useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import type { Permission } from '../api/types'
import { deconnecter } from '../auth/authApi'
import { aPermission, useSession } from '../auth/session'

const LARGEUR_MENU = 248

interface EntreeMenu {
  chemin: string
  libelle: string
  icone: ReactNode
  permission?: Permission
}

const SECTIONS: { titre: string; entrees: EntreeMenu[] }[] = [
  {
    titre: 'Pilotage',
    entrees: [{ chemin: '/', libelle: 'Tableau de bord', icone: <DashboardIcon /> }],
  },
  {
    titre: 'Exploitation',
    entrees: [{ chemin: '/journees', libelle: 'Journées', icone: <EventNoteIcon />, permission: 'REFERENTIEL_CONSULTER' }],
  },
  {
    titre: 'Référentiel',
    entrees: [
      { chemin: '/stations', libelle: 'Stations', icone: <LocalGasStationIcon />, permission: 'REFERENTIEL_CONSULTER' },
      { chemin: '/produits', libelle: 'Produits', icone: <OilBarrelIcon />, permission: 'REFERENTIEL_CONSULTER' },
      { chemin: '/prix', libelle: 'Prix', icone: <SellIcon />, permission: 'REFERENTIEL_CONSULTER' },
    ],
  },
  {
    titre: 'Administration',
    entrees: [
      { chemin: '/utilisateurs', libelle: 'Utilisateurs', icone: <PeopleIcon />, permission: 'UTILISATEUR_GERER' },
      { chemin: '/audit', libelle: "Journal d'audit", icone: <HistoryIcon />, permission: 'AUDIT_CONSULTER' },
    ],
  },
]

function initiales(nom: string): string {
  return nom
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((m) => m[0]!.toUpperCase())
    .join('')
}

export function Disposition() {
  const theme = useTheme()
  const grandEcran = useMediaQuery(theme.breakpoints.up('md'))
  const [menuOuvert, setMenuOuvert] = useState(false)
  const [ancreCompte, setAncreCompte] = useState<HTMLElement | null>(null)
  const utilisateur = useSession((e) => e.utilisateur)!
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()

  const sections = SECTIONS.map((s) => ({
    ...s,
    entrees: s.entrees.filter((e) => !e.permission || aPermission(utilisateur, e.permission)),
  })).filter((s) => s.entrees.length > 0)

  const seDeconnecter = async () => {
    setAncreCompte(null)
    await deconnecter().catch(() => undefined)
    queryClient.clear()
    navigate('/connexion', { replace: true })
  }

  const contenuMenu = (
    <Box sx={{ overflow: 'auto' }}>
      <Toolbar />
      {sections.map((section) => (
        <List
          key={section.titre}
          dense
          subheader={<ListSubheader sx={{ bgcolor: 'transparent', lineHeight: '36px' }}>{section.titre}</ListSubheader>}
        >
          {section.entrees.map((e) => {
            const actif = e.chemin === '/' ? location.pathname === '/' : location.pathname.startsWith(e.chemin)
            return (
              <ListItemButton
                key={e.chemin}
                component={NavLink}
                to={e.chemin}
                selected={actif}
                onClick={() => setMenuOuvert(false)}
                sx={{ mx: 1, borderRadius: 1 }}
              >
                <ListItemIcon sx={{ minWidth: 36 }}>{e.icone}</ListItemIcon>
                <ListItemText primary={e.libelle} />
              </ListItemButton>
            )
          })}
        </List>
      ))}
    </Box>
  )

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
        <Toolbar>
          {!grandEcran && (
            <IconButton color="inherit" edge="start" aria-label="Ouvrir le menu" onClick={() => setMenuOuvert(true)} sx={{ mr: 1 }}>
              <MenuIcon />
            </IconButton>
          )}
          <Box component="img" src="/favicon.svg" alt="" sx={{ width: 28, height: 28, mr: 1.5 }} />
          <Typography variant="h6" component="div" sx={{ flexGrow: 1, letterSpacing: 0.5 }}>
            Jaugeo
          </Typography>
          <Box sx={{ display: { xs: 'none', sm: 'block' }, textAlign: 'right', mr: 1.5 }}>
            <Typography variant="body2" sx={{ lineHeight: 1.2 }}>
              {utilisateur.nomComplet}
            </Typography>
            <Typography variant="caption" sx={{ opacity: 0.8 }}>
              {utilisateur.roleLibelle}
            </Typography>
          </Box>
          <Tooltip title="Mon compte">
            <IconButton onClick={(ev) => setAncreCompte(ev.currentTarget)} aria-label="Mon compte" sx={{ p: 0.5 }}>
              <Avatar sx={{ bgcolor: 'secondary.main', color: 'secondary.contrastText', width: 34, height: 34, fontSize: 14 }}>
                {initiales(utilisateur.nomComplet)}
              </Avatar>
            </IconButton>
          </Tooltip>
          <Menu anchorEl={ancreCompte} open={!!ancreCompte} onClose={() => setAncreCompte(null)}>
            <MenuItem
              onClick={() => {
                setAncreCompte(null)
                navigate('/mon-compte')
              }}
            >
              <ListItemIcon>
                <AccountCircleIcon fontSize="small" />
              </ListItemIcon>
              Mon compte
            </MenuItem>
            <Divider />
            <MenuItem onClick={seDeconnecter}>
              <ListItemIcon>
                <LogoutIcon fontSize="small" />
              </ListItemIcon>
              Se déconnecter
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      <Box component="nav" sx={{ width: { md: LARGEUR_MENU }, flexShrink: { md: 0 } }}>
        <Drawer
          variant={grandEcran ? 'permanent' : 'temporary'}
          open={grandEcran || menuOuvert}
          onClose={() => setMenuOuvert(false)}
          sx={{ '& .MuiDrawer-paper': { width: LARGEUR_MENU, boxSizing: 'border-box', borderRight: '1px solid #e3e7eb' } }}
        >
          {contenuMenu}
        </Drawer>
      </Box>

      <Box component="main" sx={{ flexGrow: 1, minWidth: 0, p: { xs: 2, md: 3 } }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  )
}
