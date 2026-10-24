import { Alert } from '@mui/material'
import { ApiError } from '../api/client'

export default function ErrorAlert({ error }: { error: unknown }) {
  const message = error instanceof ApiError || error instanceof Error ? error.message : 'Something went wrong.'
  const details = error instanceof ApiError ? error.errors : []
  return (
    <Alert severity="error">
      {message}
      {details.length > 0 && (
        <ul style={{ margin: '4px 0 0', paddingLeft: 20 }}>
          {details.map((d) => (
            <li key={d}>{d}</li>
          ))}
        </ul>
      )}
    </Alert>
  )
}
