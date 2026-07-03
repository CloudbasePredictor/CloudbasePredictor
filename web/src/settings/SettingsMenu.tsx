/**
 * Settings menu: a header gear button opening a small popover with the display
 * units preset and the theme mode. Both persist to `localStorage` via the
 * settings store and take effect immediately (units re-render the charts, theme
 * flips the `data-theme` attribute and the Canvas palette).
 */

import { useEffect, useId, useRef, useState } from "react";
import type { UnitPreset } from "../model/units";
import { setThemeMode, setUnitPreset, type ThemeMode } from "./settingsStore";
import { useSettings } from "./useSettings";

const UNIT_OPTIONS: Array<{ id: UnitPreset; label: string; hint: string }> = [
  { id: "METRIC_KMH", label: "Metric", hint: "km/h · m · m/s" },
  { id: "METRIC_MPS", label: "Metric (m/s)", hint: "m/s · m · m/s" },
  { id: "IMPERIAL", label: "Imperial", hint: "mph · ft · ft/min" },
  { id: "AVIATION", label: "Aviation", hint: "kt · ft · ft/min" },
];

const THEME_OPTIONS: Array<{ id: ThemeMode; label: string; icon: string }> = [
  { id: "system", label: "System", icon: "◑" },
  { id: "light", label: "Light", icon: "☀" },
  { id: "dark", label: "Dark", icon: "☾" },
];

export function SettingsMenu(): React.JSX.Element {
  const settings = useSettings();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);
  const panelId = useId();

  useEffect(() => {
    if (!open) return;
    function onPointerDown(event: PointerEvent): void {
      if (rootRef.current !== null && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    function onKeyDown(event: KeyboardEvent): void {
      if (event.key === "Escape") setOpen(false);
    }
    window.addEventListener("pointerdown", onPointerDown);
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("pointerdown", onPointerDown);
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  return (
    <div className="settings-menu" ref={rootRef}>
      <button
        type="button"
        className="header-button settings-toggle"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={panelId}
        aria-label="Settings"
        title="Settings"
        onClick={() => setOpen((value) => !value)}
      >
        <span aria-hidden="true">⚙</span>
      </button>

      {open && (
        <div className="settings-popover" id={panelId} role="dialog" aria-label="Settings">
          <fieldset className="settings-group">
            <legend>Units</legend>
            <div className="settings-options">
              {UNIT_OPTIONS.map((option) => (
                <button
                  key={option.id}
                  type="button"
                  className={`settings-option${settings.unitPreset === option.id ? " active" : ""}`}
                  aria-pressed={settings.unitPreset === option.id}
                  onClick={() => setUnitPreset(option.id)}
                >
                  <span className="settings-option-label">{option.label}</span>
                  <span className="settings-option-hint">{option.hint}</span>
                </button>
              ))}
            </div>
          </fieldset>

          <fieldset className="settings-group">
            <legend>Theme</legend>
            <div className="settings-options settings-options-row">
              {THEME_OPTIONS.map((option) => (
                <button
                  key={option.id}
                  type="button"
                  className={`settings-option${settings.themeMode === option.id ? " active" : ""}`}
                  aria-pressed={settings.themeMode === option.id}
                  onClick={() => setThemeMode(option.id)}
                >
                  <span className="settings-option-icon" aria-hidden="true">
                    {option.icon}
                  </span>
                  <span className="settings-option-label">{option.label}</span>
                </button>
              ))}
            </div>
          </fieldset>
        </div>
      )}
    </div>
  );
}
