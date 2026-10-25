# AI profiles

Shared rules for how capyreader, opentick and finance pick an AI connection. The Kotlin
code in this module implements them for capyreader. opentick and finance follow the same
rules in their own code, so a user sees the same settings and the same fallback in each app.

## Profile

```json
{ "name": "Z.AI", "baseUrl": "https://api.z.ai/api/paas/v4", "apiKey": "…", "model": "glm-4.6" }
```

- `baseUrl` is the OpenAI compatible base, up to and including the version path. The
  client appends `/chat/completions`.
- A profile with a blank `baseUrl` or `model` is unusable and skipped.
- `name` is what the user calls it. When blank, show the model.
- Profiles are stored as a JSON array. Order is priority: index 0 is tried first.
- Readers accept `baseURL` as an alias for `baseUrl` and ignore unknown fields.

## Settings shared by all profiles

- **Thinking level**: `none`, `low`, `medium` or `high`. Default `none`.
- **Custom instructions**: free text appended to the system prompt of every request, after
  the app's own prompt, under the line `Additional instructions from the user:`. Blank
  means nothing is appended.

Apps may keep extra app specific toggles next to these.

## Fallback

A request walks the usable profiles in order:

1. The first profile gets up to 3 attempts, waiting 1 s and then 2 s between them.
2. Every later profile gets 1 attempt.
3. Any failure moves on: network error, timeout, any HTTP error status, an empty reply.
   Cancellation stops the walk and is not a failure.
4. The first success is the answer. If the app streams text, the stream from a failed
   attempt is discarded and the next attempt's text replaces it.
5. If every profile fails, the error names each profile and its last error.
6. No usable profile: fail at once with "No AI profile configured".

## Thinking level on the wire

| Host | Field sent |
|---|---|
| `openrouter.ai` or a subdomain | `"reasoning": {"effort": "<level>"}` |
| `z.ai`, `bigmodel.cn` or a subdomain (GLM) | `"thinking": {"type": "disabled"}` for `none`, `"enabled"` otherwise |
| anything else | `"reasoning_effort": "<level>"`, left out for `none` |

Non-reasoning models such as gpt-4.1-mini reject `reasoning_effort`, so `none` sends nothing
there and reasoning models fall back to their default effort.

Match the domain exactly or as a dot suffix, so `quiz.ai` is not `z.ai`. Send only the one field the host reads: OpenAI rejects unknown fields with 400. If a server
still answers 400, retry the same profile once without the field before counting the attempt
as failed.

## Presets

Presets fill `name`, `baseUrl` and `model`; the user adds the key.

| Name | baseUrl | model |
|---|---|---|
| OpenAI | https://api.openai.com/v1 | gpt-4.1-mini |
| Z.AI coding plan | https://api.z.ai/api/coding/paas/v4 | glm-4.6 |
| Z.AI | https://api.z.ai/api/paas/v4 | glm-4.6 |
| Zhipu GLM | https://open.bigmodel.cn/api/paas/v4 | glm-4.6 |
| Anthropic | https://api.anthropic.com/v1 | claude-haiku-4-5 |
| Google Gemini | https://generativelanguage.googleapis.com/v1beta/openai | gemini-2.5-flash |
| OpenRouter | https://openrouter.ai/api/v1 | openai/gpt-4.1-mini |
| Ollama | http://localhost:11434/v1 | qwen3 |

Apps may change the default model per preset (opentick needs a vision model) and may point
Ollama at the emulator host `10.0.2.2`.

## Settings screen

- A list of profiles in priority order. Long press and drag to reorder.
- Tap a profile to edit it: name, preset menu, base URL, API key, model, and a Test button.
  Delete lives in the editor. An "Add profile" button opens an empty editor.
- Test sends a request to that profile alone, with no fallback to others: system "You are a calculator. Reply with
  the number only.", user "What is 17 + 25?". A reply containing `42` passes.
- Thinking level and custom instructions sit below the list and apply to every profile.
