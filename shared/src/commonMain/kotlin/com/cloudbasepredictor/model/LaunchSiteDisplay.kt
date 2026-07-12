package com.cloudbasepredictor.model

/**
 * Pure, platform-independent formatting helpers for presenting a [ParaglidingLaunchSite]. Kept in
 * `:shared` so the web selection card and any future shared UI format launch details identically.
 */
object LaunchSiteDisplay {
    private const val MAX_DESCRIPTION_LENGTH = 220

    /** Comma-separated favourable wind directions, or `null` when none are recorded. */
    fun windDirectionsSummary(site: ParaglidingLaunchSite): String? =
        site.orientations
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = ", ") { it.direction }

    /** Comma-separated activities (paragliding, hang gliding, …), or `null` when none are recorded. */
    fun activitiesSummary(site: ParaglidingLaunchSite): String? =
        site.activities
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = ", ")

    /** Launch description trimmed to a compact preview length, or `null` when absent. */
    fun shortDescription(site: ParaglidingLaunchSite): String? {
        val text = site.description?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (text.length <= MAX_DESCRIPTION_LENGTH) {
            text
        } else {
            text.take(MAX_DESCRIPTION_LENGTH).trimEnd() + "…"
        }
    }
}
