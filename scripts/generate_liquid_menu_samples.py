#!/usr/bin/env python3
"""Print independent double-precision reference samples; never edits test baselines.

Equations: sdegenaar/liquid_glass_widgets, commit
097dea6993e474d5d04db33a4f6f2ea31dd40ac1, lib/utils/liquid_morph_physics.dart.
Flutter linearToEaseOut is Cubic(.35, .91, .33, .97).
Copyright (c) 2024–2026 Sebastian Degenaar; third_party/licenses/liquid-glass-widgets.txt.
"""


def main():
    print('raw,path,size,anchor,blend,scale')
    for raw in [-.05, -.02, 0, .001, .08, .2, .3, .4, .7, .8, .999, 1, 1.03]:
        t = min(1, max(0, raw))
        undershoot = min(0, raw)
        lo, hi = 0., 1.
        for _ in range(80):
            u = (lo+hi)/2
            x = 3*(1-u)**2*u*.35 + 3*(1-u)*u*u*.33 + u**3
            if x < t:
                lo = u
            else:
                hi = u
        u = (lo+hi)/2
        size = 3*(1-u)**2*u*.91 + 3*(1-u)*u*u*.97 + u**3 + undershoot
        path = (t-1)**2 * (3.5*(t-1)+2.5) + 1 + undershoot
        anchor = min(1, max(0, 1-t/.4))
        blend = min(28, abs(path-size)*150)
        scale = 1+(raw-1)*.1 if raw > 1 else 1+raw*.55 if raw < 0 else 1
        print(','.join(f'{v:.10f}' for v in [raw, path, size, anchor, blend, scale]))


if __name__ == '__main__':
    main()
