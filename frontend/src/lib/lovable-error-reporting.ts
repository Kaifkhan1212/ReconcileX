/**
 * Error boundary reporter for the ReconcileX application.
 * Forwards React error boundary exceptions to the runtime console.
 * In production, replace this with your preferred error tracking service
 * (e.g., Sentry, Datadog, or a custom logging endpoint).
 */

type ErrorContext = Record<string, unknown>;

export function reportLovableError(error: unknown, context: ErrorContext = {}) {
  if (typeof window === "undefined") return;

  const message = describeThrown(error);
  const stack = error instanceof Error ? error.stack : undefined;

  // Log structured error to console for local debugging.
  console.error("[ReconcileX Error Boundary]", {
    message,
    route: window.location.pathname,
    ...context,
    ...(stack !== undefined && { stack }),
  });
}

const MAX_SERIALIZED_LENGTH = 2000;

function describeThrown(error: unknown): string {
  if (error instanceof Response) {
    return `Response ${error.status}${error.url ? ` at ${error.url}` : ""}`;
  }
  if (error instanceof Error) return error.message;
  if (typeof error === "string") return error;
  const { message } = (error ?? {}) as { message?: unknown };
  if (typeof message === "string" && message.length > 0) return message;
  try {
    return JSON.stringify(error)?.slice(0, MAX_SERIALIZED_LENGTH) ?? String(error);
  } catch {
    return String(error);
  }
}
