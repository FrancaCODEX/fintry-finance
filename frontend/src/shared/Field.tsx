import {
  Children,
  cloneElement,
  isValidElement,
  useId,
  type ReactElement,
  type ReactNode,
} from 'react';
/** A real label outside the control prevents option/help text from becoming its accessible name. */
export function Field({
  label,
  children,
  className = 'field',
}: {
  label: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  const generated = useId();
  const elements = Children.toArray(children);
  const control = elements.find(
    (child) =>
      isValidElement(child) &&
      ['input', 'select', 'textarea'].includes(String(child.type)),
  ) as ReactElement<{ id?: string }> | undefined;
  const id = control?.props.id || generated;
  return (
    <div className={className}>
      <label htmlFor={id}>{label}</label>
      {elements.map((child) =>
        child === control ? cloneElement(control, { id }) : child,
      )}
    </div>
  );
}
