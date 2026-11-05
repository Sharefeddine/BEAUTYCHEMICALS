package com.chemmanager.model.analytics;

import java.time.Instant;

import com.chemmanager.model.Severity;

/** One line of the recent activity feed, sourced from the audit log. */
public record ActivityEntry(long id, Instant occurredAt, String username, String action,
                            String module, String reference, Severity severity) {
}
