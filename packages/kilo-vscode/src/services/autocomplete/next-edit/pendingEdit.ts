type Input = {
  diffStartLine: number
  replacement: string
}

type Document = {
  lineCount: number
  end(line: number): number
}

export function planInsertion(input: Input, document: Document) {
  if (input.diffStartLine < document.lineCount) {
    return { line: input.diffStartLine, character: 0, text: input.replacement }
  }
  const line = Math.max(0, document.lineCount - 1)
  const text = input.replacement.endsWith("\n") ? input.replacement.slice(0, -1) : input.replacement
  return { line, character: document.end(line), text: `\n${text}` }
}
