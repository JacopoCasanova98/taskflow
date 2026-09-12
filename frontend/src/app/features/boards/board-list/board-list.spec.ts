import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, RouterLink } from '@angular/router';
import { By } from '@angular/platform-browser';
import { of, Subject, throwError } from 'rxjs';
import { BoardApi } from '../board-api';
import { Board } from '../board.models';
import { BoardList } from './board-list';

describe('Board list page', () => {
  const first: Board = {
    id: 'one',
    name: 'Product Roadmap',
    createdAt: '2026-09-11T10:00:00Z',
    updatedAt: '2026-09-11T10:00:00Z',
  };
  const second: Board = { ...first, id: 'two', name: 'Research' };
  let fixture: ComponentFixture<BoardList>;
  let element: HTMLElement;
  let load: Subject<Board[]>;
  let api: {
    listBoards: ReturnType<typeof vi.fn>;
    createBoard: ReturnType<typeof vi.fn>;
    renameBoard: ReturnType<typeof vi.fn>;
    deleteBoard: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    load = new Subject<Board[]>();
    api = {
      listBoards: vi.fn().mockReturnValue(load),
      createBoard: vi.fn().mockReturnValue(of({ ...first, id: 'new', name: 'Canonical board' })),
      renameBoard: vi.fn().mockReturnValue(of({ ...first, name: 'Canonical rename' })),
      deleteBoard: vi.fn().mockReturnValue(of(undefined)),
    };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: BoardApi, useValue: api }],
    });
    fixture = TestBed.createComponent(BoardList);
    await settle();
    element = fixture.nativeElement;
  });
  it('links the Board name without nesting mutation controls', async () => {
    await loaded();
    const link = fixture.debugElement.query(By.directive(RouterLink));
    expect(link.nativeElement.textContent.trim()).toBe(first.name);
    expect(link.nativeElement.getAttribute('href')).toBe('/boards/one');
    expect(link.nativeElement.querySelector('button')).toBeNull();
  });
  async function settle() {
    // Http mocks resolve firstValueFrom in a microtask before Angular schedules rendering.
    await Promise.resolve();
    await fixture.whenStable();
  }
  function button(text: string, scope: ParentNode = element): HTMLButtonElement {
    const found = Array.from(scope.querySelectorAll('button')).find(
      (item) => item.textContent?.trim() === text,
    );
    expect(found, 'button: ' + text).toBeDefined();
    return found!;
  }
  async function click(text: string, scope: ParentNode = element) {
    button(text, scope).click();
    await settle();
  }
  async function loaded(boards: Board[] = [first, second]) {
    load.next(boards);
    await settle();
  }
  async function fill(name: string) {
    const input = element.querySelector<HTMLInputElement>('input')!;
    input.value = name;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await settle();
  }
  async function send() {
    element
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await settle();
  }
  function fail(
    method: 'createBoard' | 'renameBoard' | 'deleteBoard',
    status = 500,
    code = 'UNKNOWN',
  ) {
    api[method].mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status, error: { code, detail: 'private server text' } }),
      ),
    );
  }

  it('renders h1, accessible loading and create action without a false empty state or duplicate load', async () => {
    expect(element.querySelector('h1')?.textContent).toBe('Boards');
    expect(element.querySelectorAll('[role="status"]')[1]?.textContent).toContain(
      'Loading boards…',
    );
    expect(element.textContent).not.toContain('No boards yet.');
    expect(button('Create board').disabled).toBe(false);
    fixture.detectChanges();
    await settle();
    expect(api.listBoards).toHaveBeenCalledTimes(1);
    expect(api.createBoard).not.toHaveBeenCalled();
  });
  it('renders server-ordered names and labelled actions with workspace links', async () => {
    await loaded();
    expect(Array.from(element.querySelectorAll('li h2')).map((h) => h.textContent)).toEqual([
      first.name,
      second.name,
    ]);
    expect(element.querySelector('[aria-label="Rename Product Roadmap"]')).not.toBeNull();
    expect(element.querySelector('[aria-label="Delete Product Roadmap"]')).not.toBeNull();
    expect(element.querySelector('a')?.getAttribute('href')).toBe('/boards/one');
  });
  it('shows useful empty state only after successful empty load', async () => {
    await loaded([]);
    expect(element.textContent).toContain('No boards yet.');
    expect(element.textContent).toContain('Create your first board to start organizing tasks.');
    expect(element.textContent).not.toContain('Loading boards…');
    expect(api.createBoard).not.toHaveBeenCalled();
  });
  it('shows safe load failure and retries', async () => {
    load.error(new Error('private server text'));
    await settle();
    expect(element.querySelector('[role="alert"]')?.textContent).toBe(
      "We couldn't load your boards.",
    );
    expect(element.textContent).not.toContain('private server text');
    expect(element.textContent).not.toContain('No boards yet.');
    const retry = new Subject<Board[]>();
    api.listBoards.mockReturnValue(retry);
    await click('Retry');
    expect(element.textContent).toContain('Loading boards…');
    expect(api.listBoards).toHaveBeenCalledTimes(2);
    retry.next([first]);
    await settle();
    expect(element.textContent).toContain(first.name);
  });

  for (const kind of ['create', 'rename'] as const) {
    describe(kind + ' Signal Form', () => {
      const method = kind === 'create' ? 'createBoard' : 'renameBoard';
      beforeEach(async () => {
        await loaded();
        await click(kind === 'create' ? 'Create board' : 'Rename');
      });
      it('labels the input and starts with the appropriate name', () => {
        const input = element.querySelector<HTMLInputElement>('input')!;
        expect(element.querySelector('label')?.getAttribute('for')).toBe(input.id);
        expect(element.querySelector('label')?.textContent).toBe('Board name');
        expect(input.value).toBe(kind === 'create' ? '' : first.name);
        expect(document.activeElement).toBe(input);
      });
      it.each([
        ['', 'Enter a board name.'],
        ['   ', 'Enter a non-blank board name.'],
        ['a'.repeat(121), 'Use no more than 120 characters.'],
      ])('blocks invalid value with visible validation (%s)', async (name, message) => {
        await fill(name);
        await send();
        expect(api[method]).not.toHaveBeenCalled();
        expect(element.textContent).toContain(message);
        expect(element.querySelector('input')?.getAttribute('aria-invalid')).toBe('true');
      });
      it('accepts exactly 120 characters', async () => {
        await fill('a'.repeat(120));
        await send();
        expect(api[method]).toHaveBeenCalledTimes(1);
      });
      it('trims edges, preserves interior spaces and closes after server success', async () => {
        await fill('  Product   Roadmap  ');
        await send();
        expect(api[method]).toHaveBeenCalledExactlyOnceWith(
          ...(kind === 'create'
            ? [{ name: 'Product   Roadmap' }]
            : [first.id, { name: 'Product   Roadmap' }]),
        );
        expect(element.querySelector('form')).toBeNull();
        expect(element.textContent).toContain(
          kind === 'create' ? 'Canonical board' : 'Canonical rename',
        );
        expect(api.listBoards).toHaveBeenCalledTimes(1);
        if (kind === 'create') {
          await click('Create board');
          expect(element.querySelector<HTMLInputElement>('input')?.value).toBe('');
        }
      });
      it('prevents concurrent submission until response and retains other Board visibility', async () => {
        const pending = new Subject<Board>();
        api[method].mockReturnValue(pending);
        await fill('Submitted');
        await send();
        await send();
        expect(api[method]).toHaveBeenCalledTimes(1);
        expect(button('Saving…').disabled).toBe(true);
        expect(button('Cancel').disabled).toBe(true);
        expect(element.textContent).toContain(second.name);
        pending.next({ ...first, id: kind === 'create' ? 'new' : first.id, name: 'Saved' });
        await settle();
        expect(element.querySelector('form')).toBeNull();
        expect(element.textContent).toContain('Saved');
      });
      it('retains name and safe error on failure, then allows retry', async () => {
        fail(method);
        await fill('Keep this name');
        await send();
        expect(element.textContent).toContain(
          "We couldn't " + kind + ' the board. Please try again.',
        );
        expect(element.textContent).not.toContain('private server text');
        expect(element.querySelector<HTMLInputElement>('input')?.value).toBe('Keep this name');
        expect(element.querySelector('li h2')?.textContent).toBe(first.name);
        api[method].mockReturnValue(of({ ...first, name: 'Retried' }));
        await send();
        expect(api[method]).toHaveBeenCalledTimes(2);
        expect(element.querySelector('form')).toBeNull();
      });
      it('shows safe backend validation feedback without clearing input', async () => {
        fail(method, 400, 'VALIDATION_FAILED');
        await fill('Submitted');
        await send();
        expect(element.textContent).toContain(
          'Enter a non-blank board name of no more than 120 characters.',
        );
        expect(element.querySelector<HTMLInputElement>('input')?.value).toBe('Submitted');
        expect(element.textContent).not.toContain('private server text');
      });
      it('cancels without mutation and preserves original Board', async () => {
        await fill('Unsubmitted');
        await click('Cancel');
        expect(api[method]).not.toHaveBeenCalled();
        expect(element.querySelector('form')).toBeNull();
        expect(element.querySelector('li h2')?.textContent).toBe(first.name);
      });
    });
  }

  it('keeps one rename editor while allowing deletion of another Board', async () => {
    await loaded();
    await click('Rename');
    expect(element.querySelectorAll('form')).toHaveLength(1);
    const other = element.querySelectorAll('li')[1];
    expect(button('Delete', other).disabled).toBe(false);
    await click('Delete', other);
    await click('Delete board');
    expect(api.deleteBoard).toHaveBeenCalledExactlyOnceWith(second.id);
    expect(element.querySelector<HTMLInputElement>('input')?.value).toBe(first.name);
  });
  it('delete selection opens explicit named confirmation; Cancel never deletes', async () => {
    await loaded();
    await click('Delete');
    expect(api.deleteBoard).not.toHaveBeenCalled();
    expect(element.querySelector('h3')?.textContent).toBe('Delete “Product Roadmap”?');
    expect(element.textContent).toContain(
      'This will permanently delete the board and its contents.',
    );
    const panel = element.querySelector('.confirmation')!;
    expect(panel.getAttribute('aria-labelledby')).toBe(element.querySelector('h3')?.id);
    await click('Cancel', panel);
    expect(api.deleteBoard).not.toHaveBeenCalled();
    expect(element.querySelector('.confirmation')).toBeNull();
  });
  it('confirmed delete submits once and removes only after server success', async () => {
    await loaded();
    const pending = new Subject<void>();
    api.deleteBoard.mockReturnValue(pending);
    await click('Delete');
    await click('Delete board');
    // Exercise the handler guard as well as the disabled UI button.
    void fixture.componentInstance.deleteBoard();
    expect(api.deleteBoard).toHaveBeenCalledExactlyOnceWith(first.id);
    expect(button('Deleting…').disabled).toBe(true);
    expect(button('Cancel').disabled).toBe(true);
    expect(element.querySelectorAll('li')).toHaveLength(2);
    pending.next(undefined);
    await settle();
    expect(element.querySelectorAll('li')).toHaveLength(1);
    expect(element.querySelector('li h2')?.textContent).toBe(second.name);
    expect(element.querySelector('.confirmation')).toBeNull();
    expect(api.listBoards).toHaveBeenCalledTimes(1);
  });
  it('deleting the last Board reveals the normal empty state', async () => {
    await loaded([first]);
    await click('Delete');
    await click('Delete board');
    expect(element.textContent).toContain('No boards yet.');
  });
  it('failed delete retains the Board and confirmation with safe retryable error', async () => {
    await loaded();
    fail('deleteBoard');
    await click('Delete');
    await click('Delete board');
    expect(element.textContent).toContain("We couldn't delete the board. Please try again.");
    expect(element.textContent).not.toContain('private server text');
    expect(element.querySelectorAll('li')).toHaveLength(2);
    api.deleteBoard.mockReturnValue(of(undefined));
    await click('Delete board');
    expect(api.deleteBoard).toHaveBeenCalledTimes(2);
    expect(element.querySelectorAll('li')).toHaveLength(1);
  });
  it.each(['rename', 'delete'] as const)(
    'reconciles a stale %s and closes its controls',
    async (action) => {
      await loaded();
      api.listBoards.mockReturnValue(of([second]));
      fail(action === 'rename' ? 'renameBoard' : 'deleteBoard', 404, 'BOARD_NOT_FOUND');
      await click(action === 'rename' ? 'Rename' : 'Delete');
      if (action === 'rename') {
        await fill('Rename');
        await send();
      } else await click('Delete board');
      expect(element.textContent).toContain('This board is no longer available.');
      expect(element.querySelector('form')).toBeNull();
      expect(element.querySelector('.confirmation')).toBeNull();
      expect(element.querySelector('li h2')?.textContent).toBe(second.name);
      expect(api.listBoards).toHaveBeenCalledTimes(2);
      expect(button('Rename').disabled).toBe(false);
    },
  );
  it('preserves a pending rename through another Board stale reload without duplicate submission', async () => {
    await loaded();
    const pending = new Subject<Board>();
    api.renameBoard.mockReturnValue(pending);
    await click('Rename');
    await fill('Saved');
    await send();
    const reload = new Subject<Board[]>();
    api.listBoards.mockReturnValue(reload);
    fail('deleteBoard', 404, 'BOARD_NOT_FOUND');
    await click('Delete', element.querySelectorAll('li')[1]);
    await click('Delete board');
    expect(element.querySelector('ul')?.hidden).toBe(true);
    reload.next([first]);
    await settle();
    expect(button('Saving…').disabled).toBe(true);
    await send();
    expect(api.renameBoard).toHaveBeenCalledTimes(1);
    pending.next({ ...first, name: 'Saved' });
    await settle();
    expect(element.querySelector('li h2')?.textContent).toBe('Saved');
  });
});
