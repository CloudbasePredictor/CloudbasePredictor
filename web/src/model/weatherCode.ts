/**
 * WMO weather-code presentation.
 *
 * Ported 1:1 from `model/WeatherCode.kt`.
 */

export interface WeatherCodePresentation {
  readonly label: string;
  readonly shortLabel: string;
}

export function presentWeatherCode(code: number): WeatherCodePresentation {
  switch (code) {
    case 0:
      return { label: "Clear sky", shortLabel: "Clear" };
    case 1:
    case 2:
    case 3:
      return { label: "Partly cloudy", shortLabel: "Cloudy" };
    case 45:
    case 48:
      return { label: "Fog", shortLabel: "Fog" };
    case 51:
    case 53:
    case 55:
    case 56:
    case 57:
      return { label: "Drizzle", shortLabel: "Drizzle" };
    case 61:
    case 63:
    case 65:
    case 66:
    case 67:
      return { label: "Rain", shortLabel: "Rain" };
    case 71:
    case 73:
    case 75:
    case 77:
      return { label: "Snow", shortLabel: "Snow" };
    case 80:
    case 81:
    case 82:
      return { label: "Rain showers", shortLabel: "Showers" };
    case 85:
    case 86:
      return { label: "Snow showers", shortLabel: "Snow" };
    case 95:
    case 96:
    case 99:
      return { label: "Thunderstorm", shortLabel: "Storm" };
    default:
      return { label: "Unknown weather", shortLabel: "Unknown" };
  }
}
