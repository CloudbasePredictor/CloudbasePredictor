/**
 * Shared domain contract for one processed local forecast day that is ready to
 * be mapped into forecast UI states.
 *
 * 1:1 port of `domain/forecast/ForecastDayViewData.kt`. The Kotlin `init`
 * blocks (`require(...)`) become constructor validations that throw on invariant
 * violations, preserving the same messages.
 *
 * Requirements for every producer that builds this model:
 * - `localDate` must use the ISO local date format `yyyy-MM-dd`.
 * - `timeSlots` must be sorted, unique, and aligned to 15-minute boundaries.
 * - `surfaceLayer` is mandatory and owns all surface-only readings.
 * - `altitudeLayers` must never include the surface. The first altitude layer
 *   starts at 50 m AGL.
 * - Vertical layers must use a strict 50 m step with no gaps between neighbours.
 * - Every layer sample list must have the same size as `timeSlots`; sample
 *   index `n` belongs to `timeSlots[n]`.
 */

export const FORECAST_TIME_STEP_MINUTES = 15;
export const FORECAST_ALTITUDE_STEP_METERS = 50;

const MINUTES_PER_DAY = 24 * 60;
const ISO_LOCAL_DATE_REGEX = /^\d{4}-\d{2}-\d{2}$/;
const FORECAST_METRIC_ID_REGEX = /^[a-z][a-z0-9_]*$/;

export class ForecastMetricId {
  readonly value: string;

  constructor(value: string) {
    if (!FORECAST_METRIC_ID_REGEX.test(value)) {
      throw new Error("Forecast metric ids must use lower-case snake-case identifiers.");
    }
    this.value = value;
  }
}

export class ForecastMetricValueViewData {
  readonly metricId: ForecastMetricId;
  readonly value: number;
  readonly unitLabel: string;
  readonly displayLabel: string | null;

  constructor(
    metricId: ForecastMetricId,
    value: number,
    unitLabel: string,
    displayLabel: string | null = null,
  ) {
    if (unitLabel.trim().length === 0) {
      throw new Error("unitLabel must not be blank.");
    }
    this.metricId = metricId;
    this.value = value;
    this.unitLabel = unitLabel;
    this.displayLabel = displayLabel;
  }
}

export class ForecastSlotValuesViewData {
  readonly values: ForecastMetricValueViewData[];

  constructor(values: ForecastMetricValueViewData[]) {
    const metricIds = values.map((it) => it.metricId.value);
    if (new Set(metricIds).size !== metricIds.length) {
      throw new Error("Each slot can contain at most one value per metric id.");
    }
    this.values = values;
  }
}

export class ForecastSurfaceLayerViewData {
  readonly samples: ForecastSlotValuesViewData[];

  constructor(samples: ForecastSlotValuesViewData[]) {
    this.samples = samples;
  }
}

export class ForecastAltitudeLayerViewData {
  readonly altitudeMetersAgl: number;
  readonly samples: ForecastSlotValuesViewData[];

  constructor(altitudeMetersAgl: number, samples: ForecastSlotValuesViewData[]) {
    if (altitudeMetersAgl < FORECAST_ALTITUDE_STEP_METERS) {
      throw new Error("Altitude layers must start above the surface.");
    }
    if (altitudeMetersAgl % FORECAST_ALTITUDE_STEP_METERS !== 0) {
      throw new Error("Altitude layers must align to the 50 m vertical grid.");
    }
    this.altitudeMetersAgl = altitudeMetersAgl;
    this.samples = samples;
  }
}

export class ForecastTimeSlot {
  readonly startMinuteOfDayLocal: number;

  constructor(startMinuteOfDayLocal: number) {
    if (!(startMinuteOfDayLocal >= 0 && startMinuteOfDayLocal < MINUTES_PER_DAY)) {
      throw new Error("Forecast time slots must fit within a single local day.");
    }
    if (startMinuteOfDayLocal % FORECAST_TIME_STEP_MINUTES !== 0) {
      throw new Error("Forecast time slots must align to 15-minute boundaries.");
    }
    this.startMinuteOfDayLocal = startMinuteOfDayLocal;
  }
}

export class ForecastDayViewData {
  readonly localDate: string;
  readonly timeSlots: ForecastTimeSlot[];
  readonly surfaceLayer: ForecastSurfaceLayerViewData;
  readonly altitudeLayers: ForecastAltitudeLayerViewData[];

  constructor(
    localDate: string,
    timeSlots: ForecastTimeSlot[],
    surfaceLayer: ForecastSurfaceLayerViewData,
    altitudeLayers: ForecastAltitudeLayerViewData[],
  ) {
    if (!ISO_LOCAL_DATE_REGEX.test(localDate)) {
      throw new Error("localDate must use yyyy-MM-dd format.");
    }
    if (timeSlots.length === 0) {
      throw new Error("timeSlots must not be empty.");
    }
    const sorted = [...timeSlots].sort((a, b) => a.startMinuteOfDayLocal - b.startMinuteOfDayLocal);
    if (
      !timeSlots.every(
        (slot, index) => slot.startMinuteOfDayLocal === sorted[index].startMinuteOfDayLocal,
      )
    ) {
      throw new Error("timeSlots must be sorted in ascending local time order.");
    }
    if (new Set(timeSlots.map((slot) => slot.startMinuteOfDayLocal)).size !== timeSlots.length) {
      throw new Error("timeSlots must be unique.");
    }
    if (surfaceLayer.samples.length !== timeSlots.length) {
      throw new Error("surfaceLayer sample count must match the shared timeSlots size.");
    }

    if (altitudeLayers.length > 0) {
      if (altitudeLayers[0].altitudeMetersAgl !== FORECAST_ALTITUDE_STEP_METERS) {
        throw new Error("The first altitude layer must start at 50 m AGL.");
      }
    }

    altitudeLayers.forEach((layer, index) => {
      if (layer.samples.length !== timeSlots.length) {
        throw new Error("Altitude layer sample count must match the shared timeSlots size.");
      }

      if (index === 0) {
        return;
      }

      const previousLayer = altitudeLayers[index - 1];
      if (
        layer.altitudeMetersAgl - previousLayer.altitudeMetersAgl !==
        FORECAST_ALTITUDE_STEP_METERS
      ) {
        throw new Error("Altitude layers must use a strict 50 m step without gaps.");
      }
    });

    this.localDate = localDate;
    this.timeSlots = timeSlots;
    this.surfaceLayer = surfaceLayer;
    this.altitudeLayers = altitudeLayers;
  }
}
