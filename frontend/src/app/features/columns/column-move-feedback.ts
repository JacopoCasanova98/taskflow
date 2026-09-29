/** Keep scroll/focus local to the Kanban; never scroll the page to restore focus. */
export function finishColumnMove(
  scroller: HTMLElement,
  positions: ReadonlyMap<HTMLElement, number>,
  trigger: HTMLElement,
  animate: boolean,
): void {
  const moved = trigger.closest<HTMLElement>('.lane')!;
  const bounds = scroller.getBoundingClientRect();
  const target = moved.getBoundingClientRect();
  const adjustment =
    target.left < bounds.left
      ? target.left - bounds.left
      : Math.max(0, target.right - bounds.right);
  // Only reveal the moved lane if needed, preserving the current offset otherwise.
  scroller.scrollLeft += adjustment;
  trigger.focus({ preventScroll: true });
  if (!animate) return;
  for (const [lane, before] of positions) {
    const delta = before - lane.offsetLeft;
    if (delta && lane.animate) {
      lane.animate([{ transform: `translateX(${delta}px)` }, { transform: 'translateX(0)' }], {
        duration: 180,
        easing: 'ease-out',
      });
    }
  }
}
