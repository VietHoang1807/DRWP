import React from 'react';

interface BadgeProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'success' | 'warning' | 'error' | 'info';
}

export function Badge({ className = '', variant = 'default', ...props }: BadgeProps) {
  const baseStyles = 'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium border h-[22px]';
  
  const variants = {
    default: 'bg-surface-container text-on-surface border-outline-variant',
    success: 'bg-[#ECFDF5] text-[#065F46] border-[#A7F3D0]',
    warning: 'bg-[#FFFBEB] text-[#92400E] border-[#FDE68A]',
    error: 'bg-[#FEF2F2] text-[#991B1B] border-[#FECACA]',
    info: 'bg-[#EFF6FF] text-[#1E40AF] border-[#BFDBFE]',
  };

  return (
    <div className={`${baseStyles} ${variants[variant]} ${className}`} {...props} />
  );
}
