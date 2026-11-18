import React from 'react';

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  error?: string;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className = '', error, ...props }, ref) => {
    return (
      <div className="w-full">
        <input
          ref={ref}
          className={`flex h-[38px] w-full rounded border bg-surface-container-lowest px-3 py-2 text-sm text-on-surface placeholder:text-outline focus:outline-none focus:border-secondary focus:ring-2 focus:ring-secondary/15 disabled:cursor-not-allowed disabled:opacity-50 ${
            error ? 'border-error text-error' : 'border-outline-variant'
          } ${className}`}
          {...props}
        />
        {error && <p className="mt-1 text-xs text-error">{error}</p>}
      </div>
    );
  }
);

Input.displayName = 'Input';
