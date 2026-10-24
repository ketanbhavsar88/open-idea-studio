import SendIcon from '@mui/icons-material/Send'
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  IconButton,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { ApiError, api } from '../api/client'
import { useInvalidateProject } from '../api/hooks'
import type { ConversationMessage } from '../api/types'

const SKIP_MESSAGE = "Let's skip this question for now."

/** AI-guided discovery chat. Works as the right-hand panel and as the full Discuss section. */
export default function ChatPanel({ projectId }: { projectId: string }) {
  const messages = useQuery({ queryKey: ['messages', projectId], queryFn: () => api.listMessages(projectId) })
  const ai = useQuery({ queryKey: ['ai-status'], queryFn: api.aiStatus, staleTime: 60_000 })
  const invalidate = useInvalidateProject(projectId)
  const [draft, setDraft] = useState('')
  const bottom = useRef<HTMLDivElement>(null)

  const turn = useMutation({
    mutationFn: (action: { kind: 'send'; content: string } | { kind: 'start' } | { kind: 'retry' }) =>
      action.kind === 'send'
        ? api.sendMessage(projectId, action.content)
        : action.kind === 'start'
          ? api.startDiscovery(projectId)
          : api.retryReply(projectId),
    onSuccess: () => setDraft(''),
    // The user message is saved even when the model fails, so always refresh.
    onSettled: invalidate,
  })

  const list = messages.data ?? []
  const last = list.at(-1)
  const awaitingReply = !turn.isPending && last?.role === 'user'
  const send = (content: string) => content.trim() && turn.mutate({ kind: 'send', content: content.trim() })

  useEffect(() => {
    bottom.current?.scrollIntoView?.({ block: 'end' })
  }, [list.length, turn.isPending])

  return (
    <Stack spacing={1.5} sx={{ height: '100%', minHeight: 0 }}>
      {ai.data && !ai.data.configured && (
        <Alert severity="warning">
          The AI provider ({ai.data.provider}) is not configured. You can still edit everything manually.
        </Alert>
      )}
      {ai.data?.provider === 'fake' && (
        <Alert severity="info">Demo mode: replies come from a fake provider, not a real model.</Alert>
      )}

      <Box sx={{ flexGrow: 1, overflowY: 'auto', minHeight: 120 }} aria-live="polite">
        {messages.isPending && <CircularProgress size={24} aria-label="Loading messages" />}
        {messages.isSuccess && list.length === 0 && !turn.isPending && (
          <Stack spacing={1} sx={{ alignItems: 'flex-start' }}>
            <Typography variant="body2" color="text.secondary">
              The AI will summarize your idea and ask one focused question at a time. Its suggestions are saved as
              proposals for you to confirm or reject.
            </Typography>
            <Button variant="contained" onClick={() => turn.mutate({ kind: 'start' })}>
              Start discovery
            </Button>
          </Stack>
        )}
        <Stack spacing={1.5}>
          {list.map((m) => (
            <MessageBubble
              key={m.id}
              message={m}
              isLatest={m === last}
              disabled={turn.isPending}
              onOption={send}
              onSkip={() => send(SKIP_MESSAGE)}
            />
          ))}
          {turn.isPending && (
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', color: 'text.secondary' }}>
              <CircularProgress size={16} />
              <Typography variant="body2">Thinking… this can take up to a minute.</Typography>
            </Stack>
          )}
        </Stack>
        <div ref={bottom} />
      </Box>

      {turn.isError && (
        <TurnError error={turn.error} canRetry={awaitingReply} onRetry={() => turn.mutate({ kind: 'retry' })} />
      )}
      {!turn.isError && awaitingReply && (
        <Alert
          severity="info"
          action={
            <Button color="inherit" size="small" onClick={() => turn.mutate({ kind: 'retry' })}>
              Get reply
            </Button>
          }
        >
          Your last message has no reply yet.
        </Alert>
      )}

      <Box
        component="form"
        onSubmit={(e) => {
          e.preventDefault()
          send(draft)
        }}
        sx={{ display: 'flex', gap: 1, alignItems: 'flex-end' }}
      >
        <TextField
          fullWidth
          multiline
          maxRows={6}
          size="small"
          placeholder="Answer, or tell the AI more about your idea…"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              send(draft)
            }
          }}
          slotProps={{ htmlInput: { maxLength: 8000, 'aria-label': 'Message' } }}
        />
        <IconButton type="submit" color="primary" aria-label="Send" disabled={!draft.trim() || turn.isPending}>
          <SendIcon />
        </IconButton>
      </Box>
    </Stack>
  )
}

function MessageBubble(props: {
  message: ConversationMessage
  isLatest: boolean
  disabled: boolean
  onOption: (option: string) => void
  onSkip: () => void
}) {
  const { message, isLatest, disabled, onOption, onSkip } = props
  const mine = message.role === 'user'
  const question = message.metadata.question
  const created = message.metadata.createdArtifacts ?? []
  return (
    <Paper
      variant="outlined"
      sx={{
        p: 1.5,
        alignSelf: mine ? 'flex-end' : 'flex-start',
        maxWidth: '92%',
        bgcolor: mine ? 'action.selected' : 'background.paper',
      }}
    >
      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
        {message.content}
      </Typography>
      {question && (
        <Box sx={{ mt: 1.5 }}>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            {question.text}
          </Typography>
          {question.why && (
            <Typography variant="caption" color="text.secondary" component="p">
              Why it matters: {question.why}
            </Typography>
          )}
          {isLatest && (
            <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 1, flexWrap: 'wrap' }}>
              {(question.options ?? []).map((o) => (
                <Chip key={o} label={o} onClick={() => onOption(o)} disabled={disabled} />
              ))}
              <Chip label="Skip for now" variant="outlined" onClick={onSkip} disabled={disabled} />
            </Stack>
          )}
        </Box>
      )}
      {created.length > 0 && (
        <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: 1 }}>
          Proposed for your review: {created.join(', ')}
        </Typography>
      )}
    </Paper>
  )
}

function TurnError({ error, canRetry, onRetry }: { error: unknown; canRetry: boolean; onRetry: () => void }) {
  const code = error instanceof ApiError ? error.code : 'unknown'
  const message = error instanceof Error ? error.message : 'The AI request failed.'
  const hint =
    code === 'model-not-configured'
      ? 'Check the AI settings in infra/.env and restart the API.'
      : 'Your message was saved. You can retry, or keep editing your project manually.'
  return (
    <Alert
      severity={code === 'model-rate-limited' ? 'warning' : 'error'}
      action={
        canRetry && (
          <Button color="inherit" size="small" onClick={onRetry}>
            Retry
          </Button>
        )
      }
    >
      {message} {hint}
    </Alert>
  )
}
