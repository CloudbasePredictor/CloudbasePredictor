# Atmospheric calculation references

`AtmosphericReferenceTest` and `AtmosphericReferenceData` in `engine/src/commonTest` provide
published numerical examples and independently derived analytic cases. They run on JVM and
Wasm without files, network access, Python, or another scientific library at test time.
They complement `GoldenFixtureTest`: no expected value here was obtained by running the
Kotlin engine or copying its forecast output.

## Sources and conventions

Sources were consulted on 2026-09-04. Versioned MetPy 1.6 documentation keeps the reference
equations stable; later MetPy releases can use different saturation/LCL formulations.

- [Unidata MetPy 1.6 dry lapse example](https://unidata.github.io/MetPy/v1.6/api/generated/metpy.calc.dry_lapse.html):
  a 15 °C parcel at 1000 hPa cools to 8.65249458, 1.92593808, and -12.91786723 °C at
  925, 850, and 700 hPa. These are published values, not newly generated engine snapshots.
- [Unidata MetPy 1.6 potential temperature](https://unidata.github.io/MetPy/v1.6/api/generated/metpy.calc.potential_temperature.html):
  Poisson's equation is `theta = T * (p0 / p)^kappa`, with absolute temperature in K.
  The published 273 K, 800 hPa example gives 290.972015 K. Here `p0 = 1000 hPa`.
- [Unidata MetPy 1.6 saturation vapor pressure](https://unidata.github.io/MetPy/v1.6/api/generated/metpy.calc.saturation_vapor_pressure.html):
  the Bolton (1980) liquid-water approximation is `es = 6.112 * exp(17.67 * t / (t + 243.5))`,
  with `t` in °C and `es` in hPa. The published 25 °C example gives 31.6742944 hPa.
  The -20 °C case also uses liquid-water saturation, not saturation over ice.
- [Unidata MetPy 1.6 mixing ratio](https://unidata.github.io/MetPy/v1.6/api/generated/metpy.calc.mixing_ratio.html):
  `r = epsilon * e / (p - e)`, with both pressures in the same units. Our independent
  humidity table uses the rounded `epsilon = 0.622`; `r` is kg/kg, and `1000*r` is g/kg.
  Inverting vapor pressure gives the dew point. These are mass mixing ratios, not specific humidity.
- [Unidata MetPy 1.6 LCL](https://unidata.github.io/MetPy/v1.6/api/generated/metpy.calc.lcl.html):
  the published launch at 943 hPa, 33 °C, and 28 °C dew point gives 877.563323 hPa.
  The other cases solve the same saturation condition independently by bisection with
  `kappa = 2/7`, rather than interpolating the engine's pressure levels.
- [Unidata MetPy 1.6 CAPE/CIN](https://unidata.github.io/MetPy/v1.6/api/generated/metpy.calc.cape_cin.html):
  buoyant energy integrates the parcel/environment temperature difference. Its virtual
  temperature formulation reduces in the dry approximation to
  `B = g * (Tparcel - Tenv) / Tenv` and energy `integral(B dz)`.
  The engine uses actual temperature and reports CIN as a positive magnitude. For a wholly
  inhibited column with no LFC it accumulates inhibition through the supplied profile top;
  the stable-column case verifies that convention, not MetPy's no-LFC return convention.

## Corpus and tolerances

All tolerances are absolute. They describe approximation/representation error, not a claim
that the forecast is meteorologically accurate to that precision.

| Case | Reference | Tolerance | Reason |
| --- | --- | --- | --- |
| Published dry ascent | Four temperatures above | 0.03 °C | Engine `kappa = 0.286` versus reference `2/7`; maximum difference is about 0.027 °C |
| Published potential temperature | 290.972015 K | 0.02 K | Rounded kappa contributes about 0.019 K |
| Identity at 1000 hPa | 20 °C = 293.15 K | 0.0001 K/°C | Float arithmetic only |
| Vapor pressure | Humidity table below | 0.00002 hPa | Rounded literals and Float exponential |
| Mixing ratio | Humidity table below | 0.00002 g/kg; 0.00000003 kg/kg | Unit conversion and Float arithmetic |
| Inverse dew point | -20, 0, 10, 25 °C | 0.0001 °C | Rounded independent ratios and Float logarithm |
| LCL pressure | 877.563323, 861.725118, 677.456602 hPa | 0.3 hPa | Rounded kappa shifts roots by 0.079–0.186 hPa; 2 hPa input spacing adds interpolation error |
| LCL MSL height | `1.5 + (pSurface - pLcl)*0.01` km | 0.003 km | Same root tolerance mapped onto the prescribed geometry |
| Neutral dry column | CAPE = CIN = 0 J/kg | CAPE 0.0001; CIN 0.01 J/kg | No saturation/free moist convection; pressure midpoint and Float roundoff |
| Stable dry column | CAPE = 0; CIN = 194.257426 J/kg | CAPE 0.0001; CIN 0.1 J/kg | Pressure-midpoint quadrature differs from the analytic integral by about 0.072 J/kg |

| Dew point (°C) | Pressure (hPa) | Vapor pressure (hPa) | Mixing ratio (g/kg) |
| --- | --- | --- | --- |
| -20 | 700 | 1.257399876 | 1.119300187 |
| 0 | 1000 | 6.112 | 3.825042661 |
| 10 | 850 | 12.271695994 | 9.111539949 |
| 25 | 1000 | 31.674294362 | 20.345851585 |

The LCL launches are `(p hPa, T °C, Td °C) = (943,33,28), (1000,20,10), (850,15,0)`.
All disable surface heating and use an explicit 1.5 km MSL origin. Their heights are a
prescribed linear geometry to check interpolation and altitude units, not a hydrostatic
pressure-to-height atmosphere.

The dry-column input contains 21 levels at 100 m spacing, from 0 to 2000 m. Its analytic
parcel has `Tparcel(z) = 293.15 - (9.81/1004)*z` K and
`p(z) = 1000*(Tparcel(z)/293.15)^(1/0.286)` hPa. This is an idealized test column with
the stated rounded constants. The neutral environment equals that parcel. The stable
environment is `1.01*Tparcel` in K while the launch parcel remains 20 °C, so the negative
buoyancy is constant: `B = -9.81*0.01/1.01 m/s²`. Its exact integrated magnitude is
`9.81*0.01/1.01*2000 = 194.257425742574 J/kg`. Both use -60 °C dew point and no heating,
so the parcel stays unsaturated over the supplied column. The stable environment is a
prescribed perturbation, not an independently hydrostatically rebalanced sounding.

## Independent reproduction

The additional values and profile inputs were calculated with Python standard-library
double-precision math, using the source equations above. No Kotlin helpers were called.
This self-contained script reproduces them; it is documentation, not a test dependency.

```python
from math import exp

def vapor_pressure(t_c):
    return 6.112 * exp(17.67 * t_c / (t_c + 243.5))

def mixing_ratio(t_c, p_hpa):
    e = vapor_pressure(t_c)
    return 0.622 * e / (p_hpa - e)

for t, p in [(-20, 700), (0, 1000), (10, 850), (25, 1000)]:
    print(t, p, vapor_pressure(t), 1000 * mixing_ratio(t, p))

for p, t, td in [(943, 33, 28), (1000, 20, 10), (850, 15, 0)]:
    conserved_ratio = mixing_ratio(td, p)
    low, high = 400.0, float(p)
    for _ in range(80):
        mid = (low + high) / 2
        parcel_c = (t + 273.15) * (mid / p) ** (2 / 7) - 273.15
        if mixing_ratio(parcel_c, mid) > conserved_ratio:
            high = mid
        else:
            low = mid
    print("LCL", p, t, td, (low + high) / 2)

for z in range(0, 2001, 100):
    tk = 293.15 - (9.81 / 1004) * z
    p = 1000 * (tk / 293.15) ** (1 / 0.286)
    print(f"DryLevel({p:.6f}f, {tk - 273.15:.6f}f, {z / 1000:.1f}f),")

print("Stable CIN", 9.81 * 0.01 / 1.01 * 2000)
```

These cases detect temperature-offset, pressure-ratio/exponent, mixing-ratio unit,
saturation-coefficient, LCL crossing/interpolation, altitude-unit, and negative-buoyancy
sign/scaling regressions. They do not independently validate the saturated ascent integrator,
positive CAPE/LFC/EL selection, CCL selection, thermal-strength calibration, or forecast skill
against observed soundings. Differences in those model assumptions need separate review;
production equations must not be changed merely to force reference agreement.
