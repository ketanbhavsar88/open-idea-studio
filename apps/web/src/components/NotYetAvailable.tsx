import { Alert, AlertTitle } from '@mui/material'

/** Explicit limitation for features not built yet, instead of a fake success state. */
export default function NotYetAvailable({ feature, phase }: { feature: string; phase: string }) {
  return (
    <Alert severity="info" variant="outlined">
      <AlertTitle>{feature} is not available yet</AlertTitle>
      This section is planned for {phase} of the MVP. Nothing here is generated or saved yet.
    </Alert>
  )
}
