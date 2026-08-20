import './ToggleSwitch.css'

interface ToggleSwitchProps {
  checked: boolean
  disabled?: boolean
  onChange: () => void
  label: string
  pendingLabel?: string
  isPending?: boolean
  ariaLabel: string
}

function ToggleSwitch({
  checked,
  disabled = false,
  onChange,
  label,
  pendingLabel,
  isPending = false,
  ariaLabel,
}: ToggleSwitchProps) {
  return (
    <span className="toggle-switch">
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={ariaLabel}
        disabled={disabled}
        onClick={onChange}
        className={`toggle-switch__track${checked ? ' toggle-switch__track--on' : ''}${
          isPending ? ' toggle-switch__track--pending' : ''
        }`}
      >
        <span className="toggle-switch__thumb" />
      </button>
      <span
        className={`toggle-switch__label${
          !isPending && checked ? ' toggle-switch__label--on' : ''
        }`}
      >
        {isPending ? pendingLabel ?? '更新中...' : label}
      </span>
    </span>
  )
}

export default ToggleSwitch
