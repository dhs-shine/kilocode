declare module "node:zlib" {
  export function zstdCompress(input: Uint8Array, callback: (err: Error | null, result: Buffer) => void): void
  export function zstdDecompress(input: Uint8Array, callback: (err: Error | null, result: Buffer) => void): void
}
