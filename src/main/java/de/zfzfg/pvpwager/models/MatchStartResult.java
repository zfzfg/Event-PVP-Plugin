package de.zfzfg.pvpwager.models;

/** Explicit responsibility for the supplied stake after a start attempt. */
public enum MatchStartResult {
    ACCEPTED, REJECTED_CALLER_OWNS_STAKE, RETURNED_OR_DURABLY_QUEUED
}
