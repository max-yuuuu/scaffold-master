import React from 'react';

type IconProps = {
  size?: number;
  className?: string;
};

export function IconArrowLeft({ size = 16, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <path d="M10 3.5L5.5 8L10 12.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconChevronRight({ size = 14, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <path d="M6 3.5L10.5 8L6 12.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconChevronDown({ size = 14, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <path d="M3.5 6L8 10.5L12.5 6" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconSearch({ size = 15, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <circle cx="7.2" cy="7.2" r="4.6" stroke="currentColor" strokeWidth="1.45" />
      <path d="M10.7 10.7L13.5 13.5" stroke="currentColor" strokeWidth="1.45" strokeLinecap="round" />
    </svg>
  );
}

export function IconUndo({ size = 15, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <path d="M5.5 4.2L2.8 6.8L5.5 9.5" stroke="currentColor" strokeWidth="1.45" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M3.2 6.8H9.8C11.7 6.8 13.2 8.3 13.2 10.2C13.2 12.1 11.7 13.5 9.8 13.5H7.2" stroke="currentColor" strokeWidth="1.45" strokeLinecap="round" />
    </svg>
  );
}

export function IconRedo({ size = 15, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <path d="M10.5 4.2L13.2 6.8L10.5 9.5" stroke="currentColor" strokeWidth="1.45" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M12.8 6.8H6.2C4.3 6.8 2.8 8.3 2.8 10.2C2.8 12.1 4.3 13.5 6.2 13.5H8.8" stroke="currentColor" strokeWidth="1.45" strokeLinecap="round" />
    </svg>
  );
}

export function IconSun({ size = 15, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <circle cx="8" cy="8" r="3" stroke="currentColor" strokeWidth="1.45" />
      <path d="M8 2V3.3M8 12.7V14M2 8H3.3M12.7 8H14M3.75 3.75L4.65 4.65M11.35 11.35L12.25 12.25M12.25 3.75L11.35 4.65M4.65 11.35L3.75 12.25" stroke="currentColor" strokeWidth="1.45" strokeLinecap="round" />
    </svg>
  );
}

export function IconMoon({ size = 15, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <path d="M12.8 9.8A5.2 5.2 0 0 1 6.2 3.2A5.2 5.2 0 1 0 12.8 9.8Z" stroke="currentColor" strokeWidth="1.45" strokeLinejoin="round" />
    </svg>
  );
}

export function IconCode({ size = 15, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <path d="M5.4 4.8L2.5 8L5.4 11.2M10.6 4.8L13.5 8L10.6 11.2" stroke="currentColor" strokeWidth="1.45" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M9 3.4L7 12.6" stroke="currentColor" strokeWidth="1.35" strokeLinecap="round" />
    </svg>
  );
}

export function IconEye({ size = 15, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <path d="M1.8 8C3.1 5.1 5.4 3.7 8 3.7C10.6 3.7 12.9 5.1 14.2 8C12.9 10.9 10.6 12.3 8 12.3C5.4 12.3 3.1 10.9 1.8 8Z" stroke="currentColor" strokeWidth="1.4" />
      <circle cx="8" cy="8" r="2" stroke="currentColor" strokeWidth="1.4" />
    </svg>
  );
}

export function IconEyeOff({ size = 15, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <path d="M2.2 2.2L13.8 13.8" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
      <path d="M6.5 3.9C7 3.77 7.5 3.7 8 3.7C10.6 3.7 12.9 5.1 14.2 8C13.6 9.3 12.8 10.3 11.8 11M4.3 5.1C3.2 5.9 2.4 6.8 1.8 8C3.1 10.9 5.4 12.3 8 12.3C9.1 12.3 10.1 12 11 11.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  );
}

export function IconLock({ size = 14, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <rect x="3.2" y="7.2" width="9.6" height="6.5" rx="2" stroke="currentColor" strokeWidth="1.4" />
      <path d="M5.3 7.2V5.2C5.3 3.7 6.5 2.5 8 2.5C9.5 2.5 10.7 3.7 10.7 5.2V7.2" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  );
}

export function IconUnlock({ size = 14, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <rect x="3.2" y="7.2" width="9.6" height="6.5" rx="2" stroke="currentColor" strokeWidth="1.4" />
      <path d="M5.3 7.2V5.2C5.3 3.7 6.5 2.5 8 2.5C9.2 2.5 10.2 3.2 10.6 4.3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  );
}

export function IconBolt({ size = 14, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <path d="M8.8 2L3.5 8.8H8L7.2 14L12.5 7.2H8L8.8 2Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconPage({ size = 15, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <rect x="2.2" y="2.2" width="11.6" height="11.6" rx="2.4" stroke="currentColor" strokeWidth="1.4" />
      <path d="M2.2 5.6H13.8M5.8 5.6V13.8" stroke="currentColor" strokeWidth="1.3" />
    </svg>
  );
}

export function IconPanelLeftClose({ size = 14, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <rect x="2" y="2.5" width="12" height="11" rx="2.2" stroke="currentColor" strokeWidth="1.35" />
      <path d="M6 2.5V13.5" stroke="currentColor" strokeWidth="1.3" />
      <path d="M10.8 6.2L8.8 8L10.8 9.8" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconPanelLeftOpen({ size = 14, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <rect x="2" y="2.5" width="12" height="11" rx="2.2" stroke="currentColor" strokeWidth="1.35" />
      <path d="M6 2.5V13.5" stroke="currentColor" strokeWidth="1.3" />
      <path d="M8.8 6.2L10.8 8L8.8 9.8" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconPanelRightClose({ size = 14, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <rect x="2" y="2.5" width="12" height="11" rx="2.2" stroke="currentColor" strokeWidth="1.35" />
      <path d="M10 2.5V13.5" stroke="currentColor" strokeWidth="1.3" />
      <path d="M5.2 6.2L7.2 8L5.2 9.8" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconPanelRightOpen({ size = 14, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <rect x="2" y="2.5" width="12" height="11" rx="2.2" stroke="currentColor" strokeWidth="1.35" />
      <path d="M10 2.5V13.5" stroke="currentColor" strokeWidth="1.3" />
      <path d="M7.2 6.2L5.2 8L7.2 9.8" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconPlus({ size = 15, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <path d="M8 3.2V12.8M3.2 8H12.8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}

export function IconTrash({ size = 15, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <path d="M3.2 4.5H12.8M6.2 4.5V3.2C6.2 2.8 6.5 2.5 6.9 2.5H9.1C9.5 2.5 9.8 2.8 9.8 3.2V4.5M4.5 4.5L5 12.5C5 13.1 5.5 13.5 6.1 13.5H9.9C10.5 13.5 11 13.1 11 12.5L11.5 4.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconCopy({ size = 15, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <rect x="5.5" y="5.5" width="7.5" height="7.5" rx="2" stroke="currentColor" strokeWidth="1.4" />
      <path d="M10.5 5.5V4C10.5 3.2 9.8 2.5 9 2.5H4C3.2 2.5 2.5 3.2 2.5 4V9C2.5 9.8 3.2 10.5 4 10.5H5.5" stroke="currentColor" strokeWidth="1.4" />
    </svg>
  );
}

export function IconCheck({ size = 12, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 12 12" fill="none" className={className} aria-hidden="true">
      <path d="M2.5 6.2L4.9 8.6L9.5 3.6" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconGrip({ size = 14, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className={className} aria-hidden="true">
      <circle cx="5" cy="3.5" r="1" fill="currentColor" />
      <circle cx="9" cy="3.5" r="1" fill="currentColor" />
      <circle cx="5" cy="7" r="1" fill="currentColor" />
      <circle cx="9" cy="7" r="1" fill="currentColor" />
      <circle cx="5" cy="10.5" r="1" fill="currentColor" />
      <circle cx="9" cy="10.5" r="1" fill="currentColor" />
    </svg>
  );
}

export function IconDesktop({ size = 15, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <rect x="2" y="2.8" width="12" height="8" rx="1.6" stroke="currentColor" strokeWidth="1.4" />
      <path d="M5.5 13.2H10.5M8 10.8V13.2" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  );
}

export function IconTablet({ size = 15, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <rect x="3.2" y="2" width="9.6" height="12" rx="1.8" stroke="currentColor" strokeWidth="1.4" />
      <circle cx="8" cy="11.8" r="0.7" fill="currentColor" />
    </svg>
  );
}

export function IconMobile({ size = 15, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <rect x="4.4" y="1.8" width="7.2" height="12.4" rx="1.8" stroke="currentColor" strokeWidth="1.4" />
      <path d="M7 12.2H9" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
    </svg>
  );
}

export function IconArrowUp({ size = 13, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <path d="M8 12.5V3.5M4.2 7L8 3.2L11.8 7" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconArrowDown({ size = 13, className = '' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className={className} aria-hidden="true">
      <path d="M8 3.5V12.5M4.2 9L8 12.8L11.8 9" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function ComponentThumb({ kind }: { kind: string }) {
  switch (kind) {
    case 'grid':
      return (
        <svg width="44" height="24" viewBox="0 0 44 24" fill="none" aria-hidden="true">
          <rect x="2" y="3" width="18" height="18" rx="3.5" fill="currentColor" fillOpacity="0.08" stroke="currentColor" strokeWidth="1.25" />
          <rect x="24" y="3" width="18" height="18" rx="3.5" fill="currentColor" fillOpacity="0.08" stroke="currentColor" strokeWidth="1.25" />
        </svg>
      );
    case 'card':
      return (
        <svg width="44" height="24" viewBox="0 0 44 24" fill="none" aria-hidden="true">
          <rect x="3" y="3" width="38" height="18" rx="4" fill="currentColor" fillOpacity="0.06" stroke="currentColor" strokeWidth="1.25" />
          <line x1="3" y1="9" x2="41" y2="9" stroke="currentColor" strokeWidth="1.1" strokeOpacity="0.55" />
          <line x1="8" y1="14" x2="22" y2="14" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
        </svg>
      );
    case 'tabs':
      return (
        <svg width="44" height="24" viewBox="0 0 44 24" fill="none" aria-hidden="true">
          <rect x="3" y="5" width="38" height="14" rx="4" fill="currentColor" fillOpacity="0.07" stroke="currentColor" strokeWidth="1.2" />
          <rect x="5.5" y="7.5" width="14" height="9" rx="2.5" fill="currentColor" fillOpacity="0.18" stroke="currentColor" strokeWidth="1.1" />
          <line x1="25" y1="12" x2="35" y2="12" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeOpacity="0.55" />
        </svg>
      );
    case 'emptySlot':
      return (
        <svg width="44" height="24" viewBox="0 0 44 24" fill="none" aria-hidden="true">
          <rect x="3" y="3" width="38" height="18" rx="4" stroke="currentColor" strokeWidth="1.25" strokeDasharray="3 2.5" />
          <path d="M22 8.5V15.5M18.5 12H25.5" stroke="currentColor" strokeWidth="1.35" strokeLinecap="round" />
        </svg>
      );
    case 'input':
      return (
        <svg width="44" height="24" viewBox="0 0 44 24" fill="none" aria-hidden="true">
          <rect x="3" y="5.5" width="38" height="13" rx="3.5" fill="currentColor" fillOpacity="0.05" stroke="currentColor" strokeWidth="1.25" />
          <line x1="8.5" y1="9" x2="8.5" y2="15" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
          <line x1="12.5" y1="12" x2="26" y2="12" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeOpacity="0.45" />
        </svg>
      );
    case 'select':
      return (
        <svg width="44" height="24" viewBox="0 0 44 24" fill="none" aria-hidden="true">
          <rect x="3" y="5.5" width="38" height="13" rx="3.5" fill="currentColor" fillOpacity="0.05" stroke="currentColor" strokeWidth="1.25" />
          <line x1="8.5" y1="12" x2="23" y2="12" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
          <path d="M32 10.5L34.5 13L37 10.5" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case 'switch':
      return (
        <svg width="44" height="24" viewBox="0 0 44 24" fill="none" aria-hidden="true">
          <rect x="10" y="5.5" width="24" height="13" rx="6.5" fill="currentColor" fillOpacity="0.14" stroke="currentColor" strokeWidth="1.25" />
          <circle cx="27.5" cy="12" r="4" fill="currentColor" />
        </svg>
      );
    case 'filter':
      return (
        <svg width="44" height="24" viewBox="0 0 44 24" fill="none" aria-hidden="true">
          <rect x="2.5" y="6" width="16" height="12" rx="3" stroke="currentColor" strokeWidth="1.2" />
          <rect x="21" y="6" width="12" height="12" rx="3" stroke="currentColor" strokeWidth="1.2" />
          <rect x="35.5" y="6" width="6" height="12" rx="2.5" fill="currentColor" fillOpacity="0.2" stroke="currentColor" strokeWidth="1.2" />
        </svg>
      );
    case 'table':
      return (
        <svg width="44" height="24" viewBox="0 0 44 24" fill="none" aria-hidden="true">
          <rect x="3" y="3" width="38" height="18" rx="3.5" stroke="currentColor" strokeWidth="1.25" />
          <line x1="3" y1="8.5" x2="41" y2="8.5" stroke="currentColor" strokeWidth="1.2" />
          <line x1="3" y1="14.5" x2="41" y2="14.5" stroke="currentColor" strokeWidth="1" strokeOpacity="0.45" />
          <line x1="15" y1="3" x2="15" y2="21" stroke="currentColor" strokeWidth="1" strokeOpacity="0.45" />
        </svg>
      );
    case 'readout':
      return (
        <svg width="44" height="24" viewBox="0 0 44 24" fill="none" aria-hidden="true">
          <rect x="3" y="3" width="38" height="18" rx="4" fill="currentColor" fillOpacity="0.06" stroke="currentColor" strokeWidth="1.2" />
          <path d="M10 16V12M16 16V8M22 16V10.5M28 16V7M34 16V9.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
      );
    case 'tag':
      return (
        <svg width="44" height="24" viewBox="0 0 44 24" fill="none" aria-hidden="true">
          <rect x="7" y="6.5" width="30" height="11" rx="5.5" fill="currentColor" fillOpacity="0.1" stroke="currentColor" strokeWidth="1.25" />
          <circle cx="13.5" cy="12" r="2" fill="currentColor" />
          <line x1="19" y1="12" x2="31" y2="12" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
        </svg>
      );
    case 'header':
      return (
        <svg width="44" height="24" viewBox="0 0 44 24" fill="none" aria-hidden="true">
          <rect x="3" y="3.5" width="38" height="17" rx="3.5" stroke="currentColor" strokeWidth="1.2" />
          <line x1="8" y1="9.5" x2="22" y2="9.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          <line x1="8" y1="14.5" x2="16" y2="14.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeOpacity="0.5" />
          <rect x="29" y="8.5" width="7.5" height="6.5" rx="2" fill="currentColor" fillOpacity="0.2" stroke="currentColor" strokeWidth="1.1" />
        </svg>
      );
    default:
      return (
        <svg width="44" height="24" viewBox="0 0 44 24" fill="none" aria-hidden="true">
          <rect x="6" y="5.5" width="32" height="13" rx="4" fill="currentColor" fillOpacity="0.16" stroke="currentColor" strokeWidth="1.25" />
          <line x1="15" y1="12" x2="29" y2="12" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
        </svg>
      );
  }
}

export function IconComponentKind({ kind, size = 15 }: { kind: string; size?: number }) {
  switch (kind) {
    case 'grid':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2.2" y="2.5" width="5" height="11" rx="1.4" stroke="currentColor" strokeWidth="1.4" />
          <rect x="8.8" y="2.5" width="5" height="11" rx="1.4" stroke="currentColor" strokeWidth="1.4" />
        </svg>
      );
    case 'card':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2.2" y="3" width="11.6" height="10" rx="2" stroke="currentColor" strokeWidth="1.4" />
          <path d="M2.2 6.2H13.8" stroke="currentColor" strokeWidth="1.35" />
        </svg>
      );
    case 'input':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2" y="4.2" width="12" height="7.6" rx="1.8" stroke="currentColor" strokeWidth="1.4" />
          <path d="M5 6.3V9.7" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
        </svg>
      );
    case 'select':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2" y="4.2" width="12" height="7.6" rx="1.8" stroke="currentColor" strokeWidth="1.4" />
          <path d="M9.5 7.2L11 8.8L12.5 7.2" stroke="currentColor" strokeWidth="1.35" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case 'switch':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2" y="4.8" width="12" height="6.4" rx="3.2" stroke="currentColor" strokeWidth="1.4" />
          <circle cx="10.8" cy="8" r="1.8" fill="currentColor" />
        </svg>
      );
    case 'button':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2.2" y="4.5" width="11.6" height="7" rx="2" stroke="currentColor" strokeWidth="1.4" />
          <path d="M5.8 8H10.2" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
        </svg>
      );
    case 'tag':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2.2" y="5" width="11.6" height="6" rx="3" stroke="currentColor" strokeWidth="1.4" />
          <circle cx="5.2" cy="8" r="1.2" fill="currentColor" />
        </svg>
      );
    case 'tabs':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2.2" y="3.2" width="11.6" height="9.6" rx="2" stroke="currentColor" strokeWidth="1.4" />
          <path d="M2.2 6.8H13.8M6.2 3.2V6.8M10 3.2V6.8" stroke="currentColor" strokeWidth="1.3" />
        </svg>
      );
    case 'divider':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path d="M2.2 8H13.8" stroke="currentColor" strokeWidth="1.45" strokeLinecap="round" />
          <path d="M5 4.5H11M5 11.5H11" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeOpacity="0.45" />
        </svg>
      );
    case 'number':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2" y="4.2" width="12" height="7.6" rx="1.8" stroke="currentColor" strokeWidth="1.4" />
          <path d="M9.8 6.4L11 5.2L12.2 6.4M9.8 9.6L11 10.8L12.2 9.6" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M5.2 6.5V9.5" stroke="currentColor" strokeWidth="1.35" strokeLinecap="round" />
        </svg>
      );
    case 'date':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2.4" y="3.4" width="11.2" height="10" rx="2" stroke="currentColor" strokeWidth="1.35" />
          <path d="M2.4 6.5H13.6M5.4 2.2V4.2M10.6 2.2V4.2" stroke="currentColor" strokeWidth="1.35" strokeLinecap="round" />
        </svg>
      );
    case 'checkbox':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2.8" y="2.8" width="10.4" height="10.4" rx="2.4" stroke="currentColor" strokeWidth="1.4" />
          <path d="M5.4 8.1L7.2 9.9L10.8 6.2" stroke="currentColor" strokeWidth="1.45" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case 'pagination':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2" y="5" width="3.4" height="6" rx="1.2" stroke="currentColor" strokeWidth="1.3" />
          <rect x="6.3" y="5" width="3.4" height="6" rx="1.2" fill="currentColor" fillOpacity="0.2" stroke="currentColor" strokeWidth="1.3" />
          <rect x="10.6" y="5" width="3.4" height="6" rx="1.2" stroke="currentColor" strokeWidth="1.3" />
        </svg>
      );
    case 'modal':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2.2" y="3" width="11.6" height="10" rx="2.2" stroke="currentColor" strokeWidth="1.4" />
          <path d="M5 6.4H11M8.2 10.2H11.2" stroke="currentColor" strokeWidth="1.35" strokeLinecap="round" />
        </svg>
      );
    case 'header':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2.2" y="2.8" width="11.6" height="4.2" rx="1.4" stroke="currentColor" strokeWidth="1.4" />
          <path d="M2.5 10.2H9.5M2.5 12.8H6.8" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
        </svg>
      );
    case 'readout':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path d="M2.5 12.5V8.5M6.2 12.5V4.5M9.8 12.5V6.8M13.5 12.5V3.2" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
        </svg>
      );
    case 'filter':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path d="M2.5 4H13.5M4.5 8H11.5M6.8 12H9.2" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
        </svg>
      );
    case 'table':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2.2" y="2.8" width="11.6" height="10.4" rx="2" stroke="currentColor" strokeWidth="1.4" />
          <path d="M2.2 6.3H13.8M6.2 6.3V13.2" stroke="currentColor" strokeWidth="1.35" />
        </svg>
      );
    case 'topology':
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <circle cx="4" cy="4.5" r="1.8" stroke="currentColor" strokeWidth="1.4" />
          <circle cx="12" cy="4.5" r="1.8" stroke="currentColor" strokeWidth="1.4" />
          <circle cx="8" cy="11.8" r="1.8" stroke="currentColor" strokeWidth="1.4" />
          <path d="M5.5 5.4L7 10.2M10.5 5.4L9 10.2" stroke="currentColor" strokeWidth="1.3" />
        </svg>
      );
    default:
      return (
        <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2.5" y="2.5" width="11" height="11" rx="2.2" stroke="currentColor" strokeWidth="1.4" />
          <path d="M5.5 6H10.5M5.5 9.5H8.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
        </svg>
      );
  }
}
