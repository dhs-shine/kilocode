import * as zlib from "node:zlib"
import { promisify } from "node:util"

type Zstd = (input: Uint8Array, callback: (err: Error | null, result: Buffer) => void) => void

const api = zlib as typeof zlib & {
  zstdCompress: Zstd
  zstdDecompress: Zstd
}

const zstdCompress = api.zstdCompress
const zstdDecompress = api.zstdDecompress
const compress = promisify(zstdCompress)
const decompress = promisify(zstdDecompress)

export async function compressZstd(bytes: Uint8Array): Promise<Uint8Array> {
  return compress(bytes) as Promise<Uint8Array>
}

export async function decompressZstd(bytes: Uint8Array): Promise<Uint8Array> {
  return decompress(bytes) as Promise<Uint8Array>
}
