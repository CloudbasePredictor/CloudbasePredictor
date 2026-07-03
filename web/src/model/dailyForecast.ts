/**
 * One day of the lightweight daily forecast.
 *
 * Ported 1:1 from `model/DailyForecast.kt`.
 */
export interface DailyForecast {
  readonly date: string;
  readonly maxTemperatureCelsius: number;
  readonly minTemperatureCelsius: number;
  readonly weatherCode: number;
}
