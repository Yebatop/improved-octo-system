# Makes the sounds in src/main/resources/assets/skirmish/sounds/fx (pip install numpy soundfile):
#   python3 tools/sfx/synth.py src/main/resources/assets/skirmish/sounds/fx
import numpy as np, soundfile as sf, os, sys
SR = 44100
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(7)

def T(sec): return np.arange(int(SR * sec)) / SR
def env(t, a, d): 
    e = np.exp(-np.maximum(t - a, 0) / d)
    if a > 0: e = np.where(t < a, t / a, e)
    return e
def sine(f, t): return np.sin(2 * np.pi * f * t)
def sweep(f0, f1, t, curve=1.0):
    dur = t[-1] if len(t) > 1 else 1
    k = (t / dur) ** curve
    f = f0 * (f1 / f0) ** k
    return np.sin(2 * np.pi * np.cumsum(f) / SR), f
def noise(n): return rng.uniform(-1, 1, n)
def lp(x, fc):
    a = np.exp(-2 * np.pi * fc / SR); y = np.zeros_like(x); s = 0.0
    for i in range(len(x)):
        s = (1 - a) * x[i] + a * s; y[i] = s
    return y
def hp(x, fc): return x - lp(x, fc)
def bp(x, lo, hi): return hp(lp(x, hi), lo)
def square(f, t, harm=9):
    y = np.zeros_like(t)
    for k in range(1, harm + 1, 2): y += np.sin(2 * np.pi * f * k * t) / k
    return y
def saw(f, t, harm=12):
    y = np.zeros_like(t)
    for k in range(1, harm + 1): y += np.sin(2 * np.pi * f * k * t) / k * (-1) ** (k + 1)
    return y
def place(dst, src, at):
    i = int(at * SR); n = min(len(src), len(dst) - i)
    if n > 0: dst[i:i + n] += src[:n]
def comb(x, d, g):
    y = x.copy(); D = int(d * SR)
    for k in range(1, (len(y) + D - 1) // D):
        a = k * D; b = min(a + D, len(y))
        y[a:b] += g * y[a - D:b - D]
    return y
def allpass(x, d, g):
    D = int(d * SR); y = np.zeros_like(x); buf = x.copy()
    for k in range(0, (len(x) + D - 1) // D):
        a = k * D; b = min(a + D, len(x))
        prev = y[a - D:b - D] if k > 0 else np.zeros(b - a)
        prevx = x[a - D:b - D] if k > 0 else np.zeros(b - a)
        y[a:b] = -g * x[a:b] + prevx + g * prev
    return y
def reverb(x, tail=1.0, wet=0.3):
    x = np.concatenate([x, np.zeros(int(SR * tail))])
    w = sum(comb(x, d, g) for d, g in [(0.0297, 0.78), (0.0371, 0.76), (0.0411, 0.74), (0.0437, 0.72)]) / 4
    w = allpass(allpass(w, 0.005, 0.7), 0.0017, 0.7)
    w = lp(w, 5000)
    return x * (1 - wet) + w * wet * 1.6
def bell(f, t, decay=0.6, partials=((1, 1), (2.76, 0.4), (5.4, 0.2), (8.93, 0.1))):
    y = np.zeros_like(t)
    for m, a in partials: y += a * sine(f * m, t) * env(t, 0.002, decay / m ** 0.5)
    return y
def finish(name, x, gain_db=-2.0):
    x = np.asarray(x, dtype=np.float64)
    x -= np.mean(x)
    thr = np.max(np.abs(x)) * 0.002
    idx = np.nonzero(np.abs(x) > thr)[0]
    if len(idx): x = x[:idx[-1] + 1]
    n = len(x); fade = min(int(0.004 * SR), n // 4)
    x[:fade] *= np.linspace(0, 1, fade)
    tail = max(fade, int(n * 0.25))
    x[-tail:] *= np.cos(np.linspace(0, np.pi / 2, tail)) ** 2
    peak = np.max(np.abs(x)) or 1
    x = x / peak * 10 ** (gain_db / 20)
    sf.write(os.path.join(OUT, name + '.ogg'), x.astype(np.float32), SR, format='OGG', subtype='VORBIS')
    print(name, round(n / SR, 2), 's')

# ---- hits ----
t = T(0.07); x = hp(noise(len(t)), 3000) * env(t, 0, 0.003) + 0.8 * sine(2900, t) * env(t, 0.001, 0.02) + 0.4 * sine(4300, t) * env(t, 0, 0.012)
finish('hit_tick', x)
t = T(0.2); s, _ = sweep(190, 55, t, 0.6); x = s * env(t, 0.002, 0.07) + 0.5 * lp(noise(len(t)), 900) * env(t, 0, 0.012)
finish('hit_punch', np.tanh(x * 1.8))
t = T(0.45); x = sum(a * sine(f, t) * env(t, 0.001, d) for f, a, d in [(2093, 1, 0.3), (3387, 0.6, 0.2), (5011, 0.35, 0.14), (6712, 0.2, 0.09)]) + 0.3 * hp(noise(len(t)), 5000) * env(t, 0, 0.004)
finish('hit_glass', x)
t = T(0.16); s, f = sweep(2400, 380, t, 0.7); ph = np.cumsum(f) / SR * 2 * np.pi
x = (np.sin(ph) + np.sin(3 * ph) / 3 + np.sin(5 * ph) / 5) * env(t, 0.002, 0.06)
finish('hit_laser', x, -4)
t = T(0.09); s, _ = sweep(280, 950, t, 0.5); x = s * env(t, 0.003, 0.025)
finish('hit_bubble', x)
t = T(0.38); x = np.zeros_like(t); a = T(0.07); place(x, square(988, a, 7) * env(a, 0.001, 0.05), 0); b = T(0.3); place(x, square(1319, b, 7) * env(b, 0.001, 0.12), 0.065)
finish('hit_coin', x, -5)
t = T(0.4); x = sum(a * sine(f, t) * env(t, 0.001, d) for f, a, d in [(520, 1, 0.22), (1342, 0.7, 0.14), (2210, 0.5, 0.1), (3170, 0.3, 0.07), (4400, 0.2, 0.05)]) + 0.5 * bp(noise(len(t)), 800, 5000) * env(t, 0, 0.01)
finish('hit_metal', x)
t = T(0.09); x = bp(noise(len(t)), 1400, 4200) * env(t, 0.001, 0.012) + 0.3 * sine(1800, t) * env(t, 0, 0.008)
finish('hit_snap', x)

# ---- crits ----
t = T(0.45); x = np.zeros_like(t)
for i, f in enumerate([2093, 2637, 3136, 4186, 5274]):
    a = T(0.3); place(x, bell(f, a, 0.18, ((1, 1), (2.0, 0.3))), i * 0.028)
x += 0.25 * hp(noise(len(t)), 6000) * env(t, 0, 0.08)
finish('crit_sparkle', x, -3)
t = T(0.45); s, _ = sweep(160, 50, t, 0.5); x = 1.2 * s * env(t, 0.002, 0.09) + sum(a * sine(f, t) * env(t, 0.001, d) for f, a, d in [(760, 0.5, 0.2), (1911, 0.35, 0.12), (3050, 0.2, 0.08)]) + 0.4 * lp(noise(len(t)), 2000) * env(t, 0, 0.02)
finish('crit_heavy', np.tanh(x * 1.5))
t = T(0.26); s, f = sweep(3000, 700, t, 0.8); buzz = np.sign(np.sin(2 * np.pi * 70 * t))
x = (0.6 * s + 0.6 * bp(noise(len(t)), 1500, 7000) * (0.5 + 0.5 * buzz)) * env(t, 0.001, 0.07)
finish('crit_zap', x, -3)

# ---- kills ----
t = T(1.1); x = np.zeros_like(t)
for i, f in enumerate([1047, 1319, 1568, 2093]):
    a = T(0.9); place(x, bell(f, a, 0.5), i * 0.07)
finish('kill_chime', reverb(x, 0.8, 0.5), -3)
t = T(0.95); x = np.zeros_like(t); w = T(0.16); place(x, lp(noise(len(w)), 3000) * (w / w[-1]) ** 2 * 0.6, 0)
d = T(0.78); s, _ = sweep(95, 38, d, 0.5); place(x, np.tanh(2.2 * s * env(d, 0.005, 0.35)), 0.15)
finish('kill_bass', x, -2)
t = T(0.7); x = np.zeros_like(t)
notes = [523, 659, 784, 1047, 1319, 1568]
for i, f in enumerate(notes):
    a = T(0.05); place(x, square(f, a, 5) * env(a, 0.001, 0.05), i * 0.045)
a = T(0.4); vib = 1 + 0.01 * np.sin(2 * np.pi * 9 * a); ph = np.cumsum(2093 * vib) / SR * 2 * np.pi
place(x, (np.sin(ph) + np.sin(3 * ph) / 3 + np.sin(5 * ph) / 5) * env(a, 0.002, 0.18), len(notes) * 0.045)
finish('kill_arcade', x, -5)
t = T(1.3); x = np.zeros_like(t)
for f in [220, 261.63, 329.63, 440]:
    for det in (-0.004, 0, 0.004):
        x += saw(f * (1 + det), t, 10)
x = bp(x, 300, 1800) * np.minimum(t / 0.12, 1) * env(t, 0.12, 0.45)
finish('kill_choir', reverb(x, 0.9, 0.6), -3)
t = T(0.9); x = np.zeros_like(t)
for k in range(26):
    at = rng.uniform(0, 0.28) ** 1.5; f = rng.uniform(2200, 9000); a = T(0.35)
    place(x, rng.uniform(0.2, 0.7) * sine(f, a) * env(a, 0.0005, rng.uniform(0.03, 0.15)), at)
x += 0.5 * hp(noise(len(t)), 2500) * env(t, 0, 0.05)
finish('kill_shatter', x, -3)
t = T(1.9); bend = 1 - 0.015 * np.minimum(t / 0.3, 1)
x = sum(a * np.sin(2 * np.pi * np.cumsum(np.full(len(t), f) * bend) / SR) * env(t, 0.004, d) for f, a, d in [(110, 1, 1.0), (179, 0.7, 0.8), (251, 0.55, 0.6), (367, 0.4, 0.45), (480, 0.3, 0.35), (703, 0.18, 0.25)])
finish('kill_gong', reverb(x, 0.6, 0.35), -2)
t = T(1.1); x = np.zeros_like(t)
for i, (f, dur) in enumerate([(392, 0.09), (523.25, 0.09), (659.25, 0.09), (783.99, 0.7)]):
    a = T(dur + 0.1); tone = saw(f, a, 14); tone = lp(tone, 2500) * np.minimum(a / 0.015, 1) * env(a, 0.015, dur * 0.8)
    place(x, tone, i * 0.1)
finish('kill_fanfare', reverb(x, 0.5, 0.35), -4)
t = T(1.3); trem = 0.75 + 0.25 * np.sin(2 * np.pi * 6 * t)
x = (sine(880, t) + 0.6 * sine(1320, t) + 0.3 * sine(1760.5, t)) * trem * np.minimum(t / 0.2, 1) * env(t, 0.2, 0.4)
x += 0.35 * bp(noise(len(t)), 800, 3000) * (t / t[-1]) * env(t, 0.5, 0.3)
finish('kill_soul', reverb(x, 0.8, 0.5), -4)

# ---- totem ----
t = T(1.2); x = np.zeros_like(t)
for i in range(14):
    f = 800 * 2 ** (i / 7); a = T(0.4); place(x, bell(f, a, 0.25, ((1, 1), (2.0, 0.25))) * 0.6, i * 0.035)
a = T(0.8); place(x, sum(bell(f, a, 0.5) for f in (1047, 1319, 1568)) * 0.5, 0.5)
finish('totem_shimmer', reverb(x, 0.6, 0.4), -3)
t = T(1.5); x = np.zeros_like(t)
for f in [261.63, 329.63, 392, 523.25]:
    for det in (-0.003, 0.003):
        x += saw(f * (1 + det) * (1 + 0.06 * np.minimum(t / 0.4, 1)), t, 10)
x = bp(x, 300, 2200) * np.minimum(t / 0.25, 1) * env(t, 0.3, 0.45)
finish('totem_choir', reverb(x, 0.9, 0.6), -3)
t = T(1.7); x = bell(523.25, t, 1.1, ((0.5, 0.6), (1, 1), (1.19, 0.5), (1.5, 0.4), (2, 0.35), (2.5, 0.2), (3, 0.15)))
finish('totem_bell', reverb(x, 0.7, 0.4), -2)
t = T(1.2); s, _ = sweep(300, 2400, t, 1.5)
x = 0.7 * bp(noise(len(t)), 500, 5000) * np.minimum(t / 0.5, 1) * env(t, 0.5, 0.25) + 0.4 * s * env(t, 0.4, 0.3)
for k in range(30):
    at = rng.uniform(0.1, 0.9); a = T(0.02); place(x, hp(noise(len(a)), 2000) * env(a, 0, 0.004) * rng.uniform(0.2, 0.6), at)
finish('totem_phoenix', x, -3)

# ---- studio ----
t = T(0.35); x = bp(noise(len(t)), 600, 3500) * np.sin(np.pi * t / t[-1]) ** 2
finish('studio_whoosh', x, -8)
