import { finishColumnMove } from './column-move-feedback';

describe('Column movement feedback', () => {
  let scroller: HTMLElement;
  let lane: HTMLElement;
  let trigger: HTMLButtonElement;
  beforeEach(() => {
    scroller = document.createElement('div');
    lane = document.createElement('section');
    lane.className = 'lane';
    trigger = document.createElement('button');
    lane.append(trigger);
    scroller.append(lane);
    document.body.append(scroller);
    scroller.scrollLeft = 350;
    vi.spyOn(scroller, 'getBoundingClientRect').mockReturnValue({
      left: 10,
      right: 340,
    } as DOMRect);
    vi.spyOn(trigger, 'focus');
  });
  afterEach(() => scroller.remove());
  it.each([
    [20, 320, 350],
    [-100, 200, 240],
    [100, 400, 410],
  ])('only reveals a lane outside the horizontal viewport (%s)', (left, right, expected) => {
    vi.spyOn(lane, 'getBoundingClientRect').mockReturnValue({ left, right } as DOMRect);
    finishColumnMove(scroller, new Map([[lane, 0]]), trigger, false);
    expect(scroller.scrollLeft).toBe(expected);
    expect(trigger.focus).toHaveBeenCalledWith({ preventScroll: true });
    expect(document.activeElement).toBe(trigger);
  });
  it('animates only displaced lanes and allows reduced motion to skip movement', () => {
    vi.spyOn(lane, 'getBoundingClientRect').mockReturnValue({ left: 20, right: 320 } as DOMRect);
    lane.animate = vi.fn();
    finishColumnMove(scroller, new Map([[lane, 0]]), trigger, true);
    expect(lane.animate).not.toHaveBeenCalled();
    finishColumnMove(scroller, new Map([[lane, 320]]), trigger, false);
    expect(lane.animate).not.toHaveBeenCalled();
    finishColumnMove(scroller, new Map([[lane, 320]]), trigger, true);
    expect(lane.animate).toHaveBeenCalledWith(
      [{ transform: 'translateX(320px)' }, { transform: 'translateX(0)' }],
      expect.any(Object),
    );
  });
  it('retains focus and scrolling when animation is unsupported', () => {
    vi.spyOn(lane, 'getBoundingClientRect').mockReturnValue({ left: 20, right: 320 } as DOMRect);
    finishColumnMove(scroller, new Map([[lane, 320]]), trigger, true);
    expect(scroller.scrollLeft).toBe(350);
    expect(document.activeElement).toBe(trigger);
  });
});
