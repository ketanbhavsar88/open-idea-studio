import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import {
  AppBar,
  Box,
  CircularProgress,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  Paper,
  Tab,
  Tabs,
  Toolbar,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import ChatPanel from '../components/ChatPanel'
import ErrorAlert from '../components/ErrorAlert'
import NotYetAvailable from '../components/NotYetAvailable'
import DecisionsSection from './DecisionsSection'
import FlowsSection from './FlowsSection'
import OverviewSection from './OverviewSection'
import RequirementsSection from './RequirementsSection'
import RevisionHistorySection from './RevisionHistorySection'

const SECTIONS = [
  { id: 'overview', label: 'Overview' },
  { id: 'discuss', label: 'Discuss' },
  { id: 'requirements', label: 'Requirements' },
  { id: 'decisions', label: 'Decisions & questions' },
  { id: 'flows', label: 'Flows' },
  { id: 'prototype', label: 'Prototype' },
  { id: 'history', label: 'Version history' },
] as const

type SectionId = (typeof SECTIONS)[number]['id']

export default function ProjectWorkspace() {
  const { projectId = '', section = 'overview' } = useParams()
  const navigate = useNavigate()
  const theme = useTheme()
  const wide = useMediaQuery(theme.breakpoints.up('md'))
  const project = useQuery({ queryKey: ['project', projectId], queryFn: () => api.getProject(projectId) })
  const current = (SECTIONS.find((s) => s.id === section) ?? SECTIONS[0]).id
  const go = (id: SectionId) => navigate(`/projects/${projectId}/${id}`)

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <AppBar position="static" color="default" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Toolbar>
          <IconButton component={RouterLink} to="/" edge="start" aria-label="Back to projects" sx={{ mr: 1 }}>
            <ArrowBackIcon />
          </IconButton>
          <Typography variant="h6" noWrap sx={{ flexGrow: 1 }}>
            {project.data?.name ?? 'Project'}
          </Typography>
        </Toolbar>
        {!wide && (
          <Tabs value={current} onChange={(_, v) => go(v)} variant="scrollable" allowScrollButtonsMobile>
            {SECTIONS.map((s) => (
              <Tab key={s.id} value={s.id} label={s.label} />
            ))}
          </Tabs>
        )}
      </AppBar>

      <Box sx={{ display: 'flex', flexGrow: 1, minHeight: 0 }}>
        {wide && (
          <Box component="nav" aria-label="Project sections" sx={{ width: 220, flexShrink: 0, borderRight: 1, borderColor: 'divider' }}>
            <List>
              {SECTIONS.map((s) => (
                <ListItemButton key={s.id} selected={s.id === current} onClick={() => go(s.id)}>
                  <ListItemText primary={s.label} />
                </ListItemButton>
              ))}
            </List>
          </Box>
        )}

        <Box component="main" sx={{ flexGrow: 1, p: { xs: 2, md: 3 }, minWidth: 0 }}>
          {project.isPending && <CircularProgress aria-label="Loading project" />}
          {project.isError && <ErrorAlert error={project.error} />}
          {project.isSuccess && <SectionContent section={current} projectId={projectId} />}
        </Box>

        {wide && current !== 'discuss' && current !== 'flows' && (
          <Paper
            component="aside"
            aria-label="AI assistant"
            square
            variant="outlined"
            sx={{
              width: 360,
              flexShrink: 0,
              p: 2,
              borderTop: 0,
              borderBottom: 0,
              borderRight: 0,
              display: 'flex',
              flexDirection: 'column',
              height: 'calc(100vh - 65px)',
              position: 'sticky',
              top: 0,
            }}
          >
            <Typography variant="subtitle1" sx={{ mb: 1 }}>
              AI assistant
            </Typography>
            {project.isSuccess && <ChatPanel projectId={projectId} />}
          </Paper>
        )}
      </Box>
    </Box>
  )
}

function SectionContent({ section, projectId }: { section: SectionId; projectId: string }) {
  switch (section) {
    case 'overview':
      return <OverviewSection projectId={projectId} />
    case 'history':
      return <RevisionHistorySection projectId={projectId} />
    case 'discuss':
      return (
        <Box sx={{ maxWidth: 820, height: { xs: 'calc(100vh - 140px)', md: 'calc(100vh - 115px)' } }}>
          <ChatPanel projectId={projectId} />
        </Box>
      )
    case 'requirements':
      return <RequirementsSection projectId={projectId} />
    case 'decisions':
      return <DecisionsSection projectId={projectId} />
    case 'flows':
      return <FlowsSection projectId={projectId} />
    case 'prototype':
      return <NotYetAvailable feature="The prototype preview" phase="Phase 3" />
  }
}
