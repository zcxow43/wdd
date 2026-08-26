import './Toast.css'

interface ToastProps {
  message: string
  onDismiss: () => void
  variant?: 'error'
}

function Toast({ message, onDismiss, variant }: ToastProps) {
  const className = variant ? `toast toast--${variant}` : 'toast'
  return (
    <div className={className} role="alert">
      <span>{message}</span>
      <button
        type="button"
        className="toast__dismiss"
        onClick={onDismiss}
        aria-label="關閉"
      >
        ×
      </button>
    </div>
  )
}

export default Toast
