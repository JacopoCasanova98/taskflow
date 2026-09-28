/** Test-only driver for the visible CDK controls and their overlay state. */
export function taskViewControls(settle: () => Promise<void>) {
  const trigger = (id: string) => document.querySelector<HTMLButtonElement>('#' + id)!;
  async function open(id: string) {
    const button = trigger(id);
    expect(button.getAttribute('aria-expanded')).toBe('false');
    button.click();
    await settle();
    expect(button.getAttribute('aria-expanded')).toBe('true');
    return document.querySelector<HTMLElement>('.filter-menu[role="menu"]')!;
  }
  async function dismiss(id: string, menu: HTMLElement) {
    menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }));
    await settle();
    expect(trigger(id).getAttribute('aria-expanded')).toBe('false');
    expect(document.querySelector('.filter-menu')).toBeNull();
  }
  return {
    trigger,
    open,
    dismiss,
    async choose(id: string, value: string) {
      const menu = await open(id);
      const option = Array.from(
        menu.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]'),
      ).find((item) => item.value === value);
      expect(option).toBeDefined();
      option!.click();
      await settle();
      expect(trigger(id).value).toBe(value);
      expect(trigger(id).getAttribute('aria-expanded')).toBe('false');
    },
    async options(id: string) {
      const menu = await open(id);
      const rows = Array.from(
        menu.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]'),
        (option) => [option.value, option.textContent!.trim()],
      );
      expect(menu.querySelector<HTMLButtonElement>('[aria-checked="true"]')?.value).toBe(
        trigger(id).value,
      );
      await dismiss(id, menu);
      return rows;
    },
  };
}
