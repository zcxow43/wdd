import './Toast.css'

interface ToastProps {
  message: string
  onDismiss: () => void
}

function Toast({ message, onDismiss }: ToastProps) {
  return (
    <div className="toast" role="alert">
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
