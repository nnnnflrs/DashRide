import { toast } from 'vue-sonner'

export interface ErrorContext {
  context: string
  showToast?: boolean
  logToConsole?: boolean
}

/**
 * Standardized error handling composable
 * Provides consistent error handling across the application
 */
export function useErrorHandler() {
  /**
   * Handle errors with consistent logging and user feedback
   * @param error - The error object
   * @param context - Context description of where the error occurred
   * @param showToast - Whether to show a toast notification (default: true)
   * @param logToConsole - Whether to log to console (default: true)
   */
  const handleError = (
    error: unknown,
    context: string,
    options: { showToast?: boolean; logToConsole?: boolean } = {}
  ) => {
    const { showToast = true, logToConsole = true } = options

    const errorMessage = error instanceof Error ? error.message : String(error)

    if (logToConsole) {
      console.error(`[${context}]`, error)
    }

    if (showToast) {
      toast.error(`${context}: ${errorMessage}`)
    }

    // Future: Report to error tracking service (e.g., Sentry)
    // trackError(context, error)
  }

  /**
   * Safely execute an async function with error handling
   * @param fn - The async function to execute
   * @param context - Context description
   * @param fallbackValue - Value to return if function fails
   * @returns Promise with the result or fallback value
   */
  const safeAsync = async <T>(
    fn: () => Promise<T>,
    context: string,
    fallbackValue: T,
    options: { showToast?: boolean; logToConsole?: boolean } = {}
  ): Promise<T> => {
    try {
      return await fn()
    } catch (error) {
      handleError(error, context, options)
      return fallbackValue
    }
  }

  /**
   * Safely execute a synchronous function with error handling
   * @param fn - The function to execute
   * @param context - Context description
   * @param fallbackValue - Value to return if function fails
   * @returns The result or fallback value
   */
  const safeSync = <T>(
    fn: () => T,
    context: string,
    fallbackValue: T,
    options: { showToast?: boolean; logToConsole?: boolean } = {}
  ): T => {
    try {
      return fn()
    } catch (error) {
      handleError(error, context, options)
      return fallbackValue
    }
  }

  return {
    handleError,
    safeAsync,
    safeSync,
  }
}
