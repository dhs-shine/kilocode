import type { QuestionRequest } from "../types/messages"

export function removeQuestion(questions: QuestionRequest[], id: string) {
  const question = questions.find((item) => item.id === id)
  if (!question) return { question, questions }
  return {
    question,
    questions: questions.filter((item) => item.id !== id),
  }
}

export function restoreQuestion(questions: QuestionRequest[], question: QuestionRequest | undefined) {
  if (!question || questions.some((item) => item.id === question.id)) return questions
  return [...questions, question]
}
