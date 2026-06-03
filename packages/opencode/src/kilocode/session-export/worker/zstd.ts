import * as zlib from "node:zlib"
import { promisify } from "node:util"

type Zstd = typeof zlib & {
  zstdCompress(input: Uint8Array, callback: (err: Error | null, result: Uint8Array) => void): void
  zstdDecompress(input: Uint8Array, callback: (err: Error | null, result: Uint8Array) => void): void
}

const runtime = zlib as Zstd
const compress = promisify(runtime.zstdCompress)
const decompress = promisify(runtime.zstdDecompress)

export async function compressZstd(bytes: Uint8Array): Promise<Uint8Array> {
  return compress(bytes) as Promise<Uint8Array>
}

export async function decompressZstd(bytes: Uint8Array): Promise<Uint8Array> {
  return decompress(bytes) as Promise<Uint8Array>
}
