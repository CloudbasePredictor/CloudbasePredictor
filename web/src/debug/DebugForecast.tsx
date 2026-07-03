import { useCallback, useState } from "react";
import { ForecastCache } from "../api/cache";
import { fetchHourlyForecast } from "../api/openMeteo";
import type { HourlyForecastData } from "../api/types";
import type { ForecastModelId } from "../model/forecastModel";

// Brauneck (Bavarian Alps) — the reference site used across the project.
const BRAUNECK_LATITUDE = 47.68;
const BRAUNECK_LONGITUDE = 11.63;
const DEBUG_MODEL: ForecastModelId = "ICON_SEAMLESS";
const DEBUG_FORECAST_DAYS = 3;

const cache = new ForecastCache();

function formatCell(value: number | null): string {
  return value === null ? "-" : String(value);
}

export function DebugForecast(): React.JSX.Element {
  const [data, setData] = useState<HourlyForecastData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    const key = {
      latitude: BRAUNECK_LATITUDE,
      longitude: BRAUNECK_LONGITUDE,
      model: DEBUG_MODEL,
    };
    try {
      const cached = cache.getFresh(key);
      if (cached !== null) {
        setData(cached);
        return;
      }
      const fetched = await fetchHourlyForecast({
        latitude: BRAUNECK_LATITUDE,
        longitude: BRAUNECK_LONGITUDE,
        model: DEBUG_MODEL,
        forecastDays: DEBUG_FORECAST_DAYS,
      });
      cache.put(key, fetched);
      setData(fetched);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : String(caught));
    } finally {
      setLoading(false);
    }
  }, []);

  return (
    <section className="debug-section">
      <h2>Data-layer debug</h2>
      <p>
        Fetches Brauneck ({BRAUNECK_LATITUDE}, {BRAUNECK_LONGITUDE}) from Open-Meteo using the{" "}
        {DEBUG_MODEL} model and renders the converted raw hourly numbers.
      </p>
      <div className="debug-controls">
        <button type="button" onClick={load} disabled={loading}>
          {loading ? "Loading..." : "Fetch Brauneck forecast"}
        </button>
        {error !== null && <span className="debug-error">Error: {error}</span>}
      </div>
      {data !== null && <ForecastDebugTable data={data} />}
    </section>
  );
}

function ForecastDebugTable({ data }: { data: HourlyForecastData }): React.JSX.Element {
  const rows = data.hourlyPoints.slice(0, 48);
  return (
    <>
      <div className="debug-meta">
        lat {data.latitude}, lon {data.longitude}, elevation{" "}
        {data.elevation === null ? "-" : `${data.elevation} m`}, timezone {data.timezone ?? "-"}{" "}
        (utc offset {data.utcOffsetSeconds}s), {data.hourlyPoints.length} hourly points,{" "}
        {data.dailyForecasts.length} daily entries. Showing first {rows.length} hours.
      </div>
      <div className="table-scroll">
        <table className="debug-table">
          <thead>
            <tr>
              <th>date</th>
              <th>hour</th>
              <th>T2m</th>
              <th>Td2m</th>
              <th>wind10m</th>
              <th>dir10m</th>
              <th>CAPE</th>
              <th>frz lvl</th>
              <th>sfc P</th>
              <th>SW rad</th>
              <th>levels</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((point) => (
              <tr key={`${point.date}T${point.hour}`}>
                <td>{point.date}</td>
                <td>{point.hour}</td>
                <td>{formatCell(point.temperature2mC)}</td>
                <td>{formatCell(point.dewPoint2mC)}</td>
                <td>{formatCell(point.windSpeed10mKmh)}</td>
                <td>{formatCell(point.windDirection10mDeg)}</td>
                <td>{formatCell(point.capeJKg)}</td>
                <td>{formatCell(point.freezingLevelHeightM)}</td>
                <td>{formatCell(point.surfacePressureHpa)}</td>
                <td>{formatCell(point.shortwaveRadiationWm2)}</td>
                <td>{point.pressureLevels.length}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
