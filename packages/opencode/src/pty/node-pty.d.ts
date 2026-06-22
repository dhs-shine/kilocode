// kilocode_change - new file
declare module "@lydell/node-pty" {
  export function spawn(file: string, args: string[] | string, opts: Opts): IPty

  export interface Opts {
    name?: string
    cols?: number
    rows?: number
    cwd?: string
    env?: Record<string, string | undefined>
    encoding?: string | null
    handleFlowControl?: boolean
    flowControlPause?: string
    flowControlResume?: string
    uid?: number
    gid?: number
    useConpty?: boolean
    useConptyDll?: boolean
    conptyInheritCursor?: boolean
  }

  export interface IPty {
    readonly pid: number
    readonly onData: (listener: (data: string) => void) => Disp
    readonly onExit: (listener: (event: Exit) => void) => Disp
    write(data: string | Buffer): void
    resize(cols: number, rows: number): void
    kill(signal?: string): void
  }

  export interface Disp {
    dispose(): void
  }

  export interface Exit {
    exitCode: number
    signal?: number
  }
}
