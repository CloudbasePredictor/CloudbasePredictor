import { describe, expect, it } from "vitest";
import {
  ForecastAltitudeLayerViewData,
  ForecastDayViewData,
  ForecastMetricId,
  ForecastMetricValueViewData,
  ForecastSlotValuesViewData,
  ForecastSurfaceLayerViewData,
  ForecastTimeSlot,
} from "./forecastDayViewData";

function surfaceSample(value: number): ForecastSlotValuesViewData {
  return new ForecastSlotValuesViewData([
    new ForecastMetricValueViewData(new ForecastMetricId("surface_temperature_c"), value, "°C"),
  ]);
}

describe("ForecastDayViewData invariants", () => {
  it("accepts a quarter-hour timeline with surface and altitude layers", () => {
    const timeSlots = [new ForecastTimeSlot(0), new ForecastTimeSlot(15), new ForecastTimeSlot(30)];
    const dayViewData = new ForecastDayViewData(
      "2026-04-12",
      timeSlots,
      new ForecastSurfaceLayerViewData(timeSlots.map((_, index) => surfaceSample(12.5 + index))),
      [
        new ForecastAltitudeLayerViewData(
          50,
          timeSlots.map((_, index) => surfaceSample(0.5 + index)),
        ),
        new ForecastAltitudeLayerViewData(
          100,
          timeSlots.map((_, index) => surfaceSample(0.25 + index)),
        ),
      ],
    );
    expect(dayViewData.timeSlots).toHaveLength(3);
    expect(dayViewData.altitudeLayers).toHaveLength(2);
  });

  it("rejects time slots off the 15-minute grid", () => {
    expect(() => new ForecastTimeSlot(7)).toThrow("15-minute boundaries");
  });

  it("rejects altitude layers off the 50 m grid", () => {
    expect(() => new ForecastAltitudeLayerViewData(75, [])).toThrow("50 m vertical grid");
  });

  it("rejects metric ids that are not lower-case snake case", () => {
    expect(() => new ForecastMetricId("Surface Temp")).toThrow("lower-case snake-case");
  });

  it("rejects a blank unit label", () => {
    expect(() => new ForecastMetricValueViewData(new ForecastMetricId("t"), 1, "   ")).toThrow(
      "unitLabel must not be blank",
    );
  });

  it("rejects duplicate metric ids in one slot", () => {
    expect(
      () =>
        new ForecastSlotValuesViewData([
          new ForecastMetricValueViewData(new ForecastMetricId("t"), 1, "°C"),
          new ForecastMetricValueViewData(new ForecastMetricId("t"), 2, "°C"),
        ]),
    ).toThrow("at most one value per metric id");
  });

  it("rejects an unsorted timeline", () => {
    expect(
      () =>
        new ForecastDayViewData(
          "2026-04-12",
          [new ForecastTimeSlot(30), new ForecastTimeSlot(15)],
          new ForecastSurfaceLayerViewData([surfaceSample(1), surfaceSample(2)]),
          [],
        ),
    ).toThrow("sorted in ascending local time order");
  });

  it("rejects a bad local date format", () => {
    expect(
      () =>
        new ForecastDayViewData(
          "2026/04/12",
          [new ForecastTimeSlot(0)],
          new ForecastSurfaceLayerViewData([surfaceSample(1)]),
          [],
        ),
    ).toThrow("yyyy-MM-dd format");
  });
});
