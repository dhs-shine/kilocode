import { promisify } from "node:util"
import * as zlib from "node:zlib"

type Zstd = (bytes: Uint8Array, cb: (err: Error | null, output: Uint8Array) => void) => void

const api = zlib as unknown as {
  zstdCompress: Zstd
  zstdDecompress: Zstd
}

const compress = promisify(api.zstdCompress)
const decompress = promisify(api.zstdDecompress)

export async function compressZstd(bytes: Uint8Array): Promise<Uint8Array> {
  return compress(bytes) as Promise<Uint8Array>
}

export async function decompressZstd(bytes: Uint8Array): Promise<Uint8Array> {
  return decompress(bytes) as Promise<Uint8Array>
}
