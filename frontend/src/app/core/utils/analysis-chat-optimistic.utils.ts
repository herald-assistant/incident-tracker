import { AnalysisChatMessageResponse } from '../models/analysis.models';

export interface AnalysisChatSnapshot {
  chatMessages: AnalysisChatMessageResponse[];
  updatedAt: string;
}

export function appendOptimisticChatTurn<T extends AnalysisChatSnapshot>(
  snapshot: T,
  message: string,
  now: string = new Date().toISOString()
): T {
  return {
    ...snapshot,
    updatedAt: now,
    chatMessages: [
      ...snapshot.chatMessages,
      chatMessage(`optimistic-user-${now}`, 'USER', 'COMPLETED', message, now, now),
      chatMessage(`optimistic-assistant-${now}`, 'ASSISTANT', 'IN_PROGRESS', '', now, '')
    ]
  };
}

function chatMessage(
  id: string,
  role: 'USER' | 'ASSISTANT',
  status: 'COMPLETED' | 'IN_PROGRESS',
  content: string,
  timestamp: string,
  completedAt: string
): AnalysisChatMessageResponse {
  return {
    id,
    role,
    status,
    content,
    errorCode: '',
    errorMessage: '',
    createdAt: timestamp,
    updatedAt: timestamp,
    completedAt,
    toolEvidenceSections: [],
    aiActivityEvents: [],
    toolFeedback: [],
    prompt: ''
  };
}
