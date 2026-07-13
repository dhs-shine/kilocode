import { addAbortSignal, Readable } from "stream"
import * as Encoding from "./encoding"

/**
 * Encoding-aware text streaming for tools that walk a file line by line.
 * Optimistically stream as UTF-8; fall back to a buffered iconv decode only
 * when the bytes turn out not to be valid UTF-8.
 *
 *   import * as TextStream from "../kilocode/text-stream"
 */

/** Distinct class so {@link withFallback} can tell us apart from real I/O failures. */
export class InvalidUtf8Error extends Error {
  constructor() {
    super("invalid utf-8")
  }
}

function decode(decoder: TextDecoder, bytes?: Uint8Array) {
  try {
    return decoder.decode(bytes, bytes ? { stream: true } : undefined)
  } catch {
    throw new InvalidUtf8Error()
  }
}

async function* chunks(open: () => Readable) {
  const decoder = new TextDecoder("utf-8", { fatal: true })
  for await (const bytes of open()) {
    const text = decode(decoder, bytes)
    if (text) yield text
  }
  const tail = decode(decoder)
  if (tail) yield tail
}

export function abortable(stream: Readable, signal?: AbortSignal) {
  return signal ? addAbortSignal(signal, stream) : stream
}

/** UTF-8 text stream backed by an already-open file. */
export function openUtf8(open: () => Readable, signal?: AbortSignal): Readable {
  return abortable(Readable.from(chunks(open)), signal)
}

export function safeSlice(text: string, end: number) {
  const sliced = text.slice(0, end)
  const last = sliced.charCodeAt(sliced.length - 1)
  return last >= 0xd800 && last <= 0xdbff ? sliced.slice(0, -1) : sliced
}

/** Whole-file decoded Readable; buffers legacy encodings only after UTF-8 streaming fails. */
export async function openDecoded(read: (signal?: AbortSignal) => Promise<Buffer>, signal?: AbortSignal) {
  const bytes = await read(signal)
  return abortable(Readable.from([Encoding.decode(bytes, Encoding.detect(bytes))]), signal)
}

/**
 * Run `fn` against an optimistic UTF-8 stream; on {@link InvalidUtf8Error}
 * retry once against {@link openDecoded}. Other errors propagate.
 */
export async function withFallback<T>(
  open: () => Readable,
  read: (signal?: AbortSignal) => Promise<Buffer>,
  fn: (input: Readable) => Promise<T>,
  signal?: AbortSignal,
): Promise<T> {
  try {
    return await fn(openUtf8(open, signal))
  } catch (err) {
    if (!(err instanceof InvalidUtf8Error)) throw err
  }
  return fn(await openDecoded(read, signal))
}
