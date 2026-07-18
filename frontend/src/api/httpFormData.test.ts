import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

test('apiPostForm leaves multipart Content-Type unset so the browser adds its boundary', async () => {
  const source = await readFile(new URL('./http.ts', import.meta.url), 'utf8')
  assert.match(
    source,
    /init\?\.body !== undefined && !\(init\.body instanceof FormData\) && !headers\.has\('Content-Type'\)/,
  )
})
