import mammoth from "mammoth"
import * as path from "path"
import { Readable } from "stream"

export function accepts(filepath: string) {
  return path.extname(filepath).toLowerCase() === ".docx"
}

export async function open(filepath: string) {
  const result = await mammoth.extractRawText({ path: filepath }).catch((err: unknown) => {
    const message = err instanceof Error ? err.message : String(err)
    throw new Error(`Failed to extract text from DOCX file: ${filepath}\n${message}`, { cause: err })
  })
  return Readable.from([result.value])
}
