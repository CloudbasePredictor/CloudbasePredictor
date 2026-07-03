import { useSyncExternalStore } from "react";
import { DebugForecast } from "./debug/DebugForecast";
import { ForecastApp } from "./forecast/ForecastApp";

const REPO_URL = "https://github.com/cloudbasepredictor/CloudbasePredictor";

function subscribeHash(callback: () => void): () => void {
  window.addEventListener("hashchange", callback);
  return () => window.removeEventListener("hashchange", callback);
}

function useHash(): string {
  return useSyncExternalStore(subscribeHash, () => window.location.hash);
}

export function App(): React.JSX.Element {
  const hash = useHash();
  if (hash.startsWith("#/debug")) {
    return <DebugPage />;
  }
  return <ForecastApp />;
}

function DebugPage(): React.JSX.Element {
  return (
    <main className="app">
      <header className="app-header">
        <h1>Cloudbase Predictor — data debug</h1>
        <p>
          Phase 1 raw data-layer page. <a href="#/">Back to the forecast</a> ·{" "}
          <a href={REPO_URL} target="_blank" rel="noreferrer">
            Source on GitHub
          </a>
        </p>
      </header>
      <DebugForecast />
    </main>
  );
}
